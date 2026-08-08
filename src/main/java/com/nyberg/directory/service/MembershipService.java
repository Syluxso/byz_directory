package com.nyberg.directory.service;

import com.nyberg.directory.client.IamRoleClient;
import com.nyberg.directory.domain.DirTenant;
import com.nyberg.directory.domain.Membership;
import com.nyberg.directory.domain.Profile;
import com.nyberg.directory.dto.DirectoryDtos.*;
import com.nyberg.directory.messaging.MembershipLifecycleApplicationEvent;
import com.nyberg.directory.messaging.MembershipLifecycleEvent;
import com.nyberg.directory.messaging.TenantLifecycleEvent;
import com.nyberg.directory.messaging.TenantMemberJoinedApplicationEvent;
import com.nyberg.directory.repository.MembershipRepository;
import com.nyberg.directory.repository.ProfileRepository;
import com.nyberg.directory.security.AuthSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MembershipService {

    private final MembershipRepository memberships;
    private final ProfileRepository profiles;
    private final TenantService tenants;
    private final OrgRoleService orgRoles;
    private final AuthSupport auth;
    private final IamRoleClient iamRoles;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public List<MembershipResponse> listMembers(UUID organizationId, UUID tenantId) {
        tenants.requireTenant(organizationId, tenantId);
        // Active + blocked (removed are soft-deleted and hidden).
        List<Membership> rows = memberships.findByTenantIdAndStatusIn(
                tenantId, List.of(Membership.STATUS_ACTIVE, Membership.STATUS_BLOCKED));
        Map<UUID, Profile> profileMap = profiles.findByOrganizationId(organizationId).stream()
                .collect(Collectors.toMap(Profile::getUserId, Function.identity(), (a, b) -> a));
        return rows.stream().map(m -> toResponse(m, profileMap.get(m.getUserId()))).toList();
    }

    @Transactional(readOnly = true)
    public List<MembershipResponse> listMyMemberships(UUID userId, UUID organizationId) {
        return memberships.findByUserIdAndOrganizationIdAndStatus(
                        userId, organizationId, Membership.STATUS_ACTIVE)
                .stream()
                .map(m -> toResponse(m, null))
                .toList();
    }

    @Transactional
    public MembershipResponse addMember(UUID organizationId, UUID tenantId, UUID actorUserId, AddMemberRequest req) {
        DirTenant tenant = tenants.requireTenant(organizationId, tenantId);
        requireTenantAdminOrOrgAdmin(organizationId, tenantId, actorUserId);
        String role = normalizeRole(req.role());

        Membership existing = memberships.findByTenantIdAndUserId(tenantId, req.userId()).orElse(null);
        if (existing != null && existing.isActive()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "User already a member");
        }
        if (existing != null && existing.isBlocked()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "User is blocked; unblock first");
        }

        Membership m;
        boolean revived = existing != null && existing.isRemoved();
        if (revived) {
            existing.setRole(role);
            existing.setStatus(Membership.STATUS_ACTIVE);
            existing.setDeletedAt(null);
            m = memberships.save(existing);
        } else {
            m = memberships.save(Membership.builder()
                    .tenantId(tenantId)
                    .userId(req.userId())
                    .organizationId(organizationId)
                    .role(role)
                    .status(Membership.STATUS_ACTIVE)
                    .build());
        }
        iamRoles.syncTenantRole(organizationId, tenantId, req.userId(), role);
        publishJoined(organizationId, tenant, actorUserId, m);
        Profile profile = profiles.findByUserIdAndOrganizationId(req.userId(), organizationId).orElse(null);
        return toResponse(m, profile);
    }

    @Transactional
    public MembershipResponse updateRole(
            UUID organizationId, UUID tenantId, UUID targetUserId, UUID actorUserId, UpdateMemberRoleRequest req) {
        DirTenant tenant = tenants.requireTenant(organizationId, tenantId);
        requireTenantAdminOrOrgAdmin(organizationId, tenantId, actorUserId);
        Membership m = requireActiveOrBlocked(tenantId, targetUserId);
        if (m.isBlocked()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot change role of a blocked member");
        }
        String newRole = normalizeRole(req.role());
        String previous = m.getRole();
        if (previous.equals(newRole)) {
            return toResponse(m, profiles.findByUserIdAndOrganizationId(targetUserId, organizationId).orElse(null));
        }
        if (Membership.ROLE_ADMIN.equals(previous) && !Membership.ROLE_ADMIN.equals(newRole)) {
            ensureNotLastAdmin(tenantId, targetUserId);
        }
        m.setRole(newRole);
        Membership saved = memberships.save(m);
        iamRoles.syncTenantRole(organizationId, tenantId, targetUserId, saved.getRole());

        ActorInfo actor = actorInfo(organizationId, actorUserId);
        Profile target = profiles.findByUserIdAndOrganizationId(targetUserId, organizationId).orElse(null);
        events.publishEvent(new MembershipLifecycleApplicationEvent(this, MembershipLifecycleEvent.of(
                MembershipLifecycleEvent.TYPE_MEMBERSHIP_ROLE_CHANGED,
                organizationId, tenantId, tenant.getName(), tenant.getSlug(),
                actorUserId, actor.displayName(), actor.email(),
                targetUserId, emailOf(target), displayOf(target),
                newRole, previous, saved.getStatus(), null
        )));
        return toResponse(saved, target);
    }

    @Transactional
    public void removeMember(UUID organizationId, UUID tenantId, UUID targetUserId, UUID actorUserId) {
        DirTenant tenant = tenants.requireTenant(organizationId, tenantId);
        requireTenantAdminOrOrgAdmin(organizationId, tenantId, actorUserId);
        Membership m = memberships.findByTenantIdAndUserId(tenantId, targetUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Membership not found"));
        if (m.isRemoved()) {
            return;
        }
        if (m.isAdmin() && m.isActive()) {
            ensureNotLastAdmin(tenantId, targetUserId);
        }
        softRemove(m, organizationId, tenant, actorUserId, MembershipLifecycleEvent.TYPE_MEMBERSHIP_REMOVED);
    }

    /** Self-service leave. Last active admin cannot leave. */
    @Transactional
    public void leave(UUID organizationId, UUID tenantId, UUID userId) {
        DirTenant tenant = tenants.requireTenant(organizationId, tenantId);
        Membership m = memberships.findByTenantIdAndUserId(tenantId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Membership not found"));
        if (m.isRemoved()) {
            return;
        }
        if (!m.isActive()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Membership is " + m.getStatus());
        }
        if (m.isAdmin()) {
            ensureNotLastAdmin(tenantId, userId);
        }
        softRemove(m, organizationId, tenant, userId, MembershipLifecycleEvent.TYPE_MEMBERSHIP_LEFT);
    }

    @Transactional
    public MembershipResponse block(UUID organizationId, UUID tenantId, UUID targetUserId, UUID actorUserId) {
        DirTenant tenant = tenants.requireTenant(organizationId, tenantId);
        requireTenantAdminOrOrgAdmin(organizationId, tenantId, actorUserId);
        if (targetUserId.equals(actorUserId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot block yourself");
        }
        Membership m = requireActiveOrBlocked(tenantId, targetUserId);
        if (m.isBlocked()) {
            return toResponse(m, profiles.findByUserIdAndOrganizationId(targetUserId, organizationId).orElse(null));
        }
        if (m.isAdmin()) {
            ensureNotLastAdmin(tenantId, targetUserId);
        }
        m.setStatus(Membership.STATUS_BLOCKED);
        Membership saved = memberships.save(m);
        iamRoles.revokeTenantRoles(organizationId, tenantId, targetUserId);

        ActorInfo actor = actorInfo(organizationId, actorUserId);
        Profile target = profiles.findByUserIdAndOrganizationId(targetUserId, organizationId).orElse(null);
        events.publishEvent(new MembershipLifecycleApplicationEvent(this, MembershipLifecycleEvent.of(
                MembershipLifecycleEvent.TYPE_MEMBERSHIP_BLOCKED,
                organizationId, tenantId, tenant.getName(), tenant.getSlug(),
                actorUserId, actor.displayName(), actor.email(),
                targetUserId, emailOf(target), displayOf(target),
                saved.getRole(), null, saved.getStatus(), null
        )));
        return toResponse(saved, target);
    }

    @Transactional
    public MembershipResponse unblock(UUID organizationId, UUID tenantId, UUID targetUserId, UUID actorUserId) {
        DirTenant tenant = tenants.requireTenant(organizationId, tenantId);
        requireTenantAdminOrOrgAdmin(organizationId, tenantId, actorUserId);
        Membership m = requireActiveOrBlocked(tenantId, targetUserId);
        if (m.isActive()) {
            return toResponse(m, profiles.findByUserIdAndOrganizationId(targetUserId, organizationId).orElse(null));
        }
        m.setStatus(Membership.STATUS_ACTIVE);
        Membership saved = memberships.save(m);
        iamRoles.syncTenantRole(organizationId, tenantId, targetUserId, saved.getRole());

        ActorInfo actor = actorInfo(organizationId, actorUserId);
        Profile target = profiles.findByUserIdAndOrganizationId(targetUserId, organizationId).orElse(null);
        events.publishEvent(new MembershipLifecycleApplicationEvent(this, MembershipLifecycleEvent.of(
                MembershipLifecycleEvent.TYPE_MEMBERSHIP_UNBLOCKED,
                organizationId, tenantId, tenant.getName(), tenant.getSlug(),
                actorUserId, actor.displayName(), actor.email(),
                targetUserId, emailOf(target), displayOf(target),
                saved.getRole(), null, saved.getStatus(), null
        )));
        return toResponse(saved, target);
    }

    /**
     * Upsert an active membership (used by invite accept / tenant create). Returns whether a join event
     * should be emitted (new or revived).
     */
    @Transactional
    public Membership ensureActiveMembership(
            UUID organizationId, UUID tenantId, UUID userId, String role) {
        String normalized = normalizeRole(role);
        Membership existing = memberships.findByTenantIdAndUserId(tenantId, userId).orElse(null);
        if (existing != null && existing.isActive()) {
            return existing;
        }
        if (existing != null && existing.isBlocked()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "User is blocked in this workspace");
        }
        if (existing != null) {
            existing.setRole(normalized);
            existing.setStatus(Membership.STATUS_ACTIVE);
            existing.setDeletedAt(null);
            Membership saved = memberships.save(existing);
            iamRoles.syncTenantRole(organizationId, tenantId, userId, normalized);
            return saved;
        }
        Membership m = memberships.save(Membership.builder()
                .tenantId(tenantId)
                .userId(userId)
                .organizationId(organizationId)
                .role(normalized)
                .status(Membership.STATUS_ACTIVE)
                .build());
        iamRoles.syncTenantRole(organizationId, tenantId, userId, normalized);
        return m;
    }

    public void publishJoined(UUID organizationId, DirTenant tenant, UUID actorUserId, Membership m) {
        publishTenantMemberJoinedOnly(organizationId, tenant, m.getUserId());
        ActorInfo actor = actorInfo(organizationId, actorUserId);
        Profile target = profiles.findByUserIdAndOrganizationId(m.getUserId(), organizationId).orElse(null);
        events.publishEvent(new MembershipLifecycleApplicationEvent(this, MembershipLifecycleEvent.of(
                MembershipLifecycleEvent.TYPE_MEMBERSHIP_JOINED,
                organizationId, tenant.getId(), tenant.getName(), tenant.getSlug(),
                actorUserId, actor.displayName(), actor.email(),
                m.getUserId(), emailOf(target), displayOf(target),
                m.getRole(), null, m.getStatus(), null
        )));
    }

    /** Publishes only {@code tenant.member_joined} (guided-task completion); no membership topic event. */
    public void publishTenantMemberJoinedOnly(UUID organizationId, DirTenant tenant, UUID userId) {
        events.publishEvent(new TenantMemberJoinedApplicationEvent(
                this,
                TenantLifecycleEvent.memberJoined(
                        organizationId,
                        tenant.getId(),
                        userId,
                        tenant.getName(),
                        tenant.getSlug()
                )
        ));
    }

    private void softRemove(
            Membership m,
            UUID organizationId,
            DirTenant tenant,
            UUID actorUserId,
            String eventType
    ) {
        m.setStatus(Membership.STATUS_REMOVED);
        m.setDeletedAt(Instant.now());
        memberships.save(m);
        iamRoles.revokeTenantRoles(organizationId, tenant.getId(), m.getUserId());

        ActorInfo actor = actorInfo(organizationId, actorUserId);
        Profile target = profiles.findByUserIdAndOrganizationId(m.getUserId(), organizationId).orElse(null);
        events.publishEvent(new MembershipLifecycleApplicationEvent(this, MembershipLifecycleEvent.of(
                eventType,
                organizationId, tenant.getId(), tenant.getName(), tenant.getSlug(),
                actorUserId, actor.displayName(), actor.email(),
                m.getUserId(), emailOf(target), displayOf(target),
                m.getRole(), null, m.getStatus(), null
        )));
    }

    private void ensureNotLastAdmin(UUID tenantId, UUID targetUserId) {
        long admins = memberships.countActiveAdmins(tenantId);
        Membership m = memberships.findByTenantIdAndUserId(tenantId, targetUserId).orElse(null);
        if (m != null && m.isAdmin() && m.isActive() && admins <= 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot remove or demote the last admin of this workspace");
        }
    }

    private Membership requireActiveOrBlocked(UUID tenantId, UUID userId) {
        Membership m = memberships.findByTenantIdAndUserId(tenantId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Membership not found"));
        if (m.isRemoved()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Membership not found");
        }
        return m;
    }

    public void requireTenantAdmin(UUID tenantId, UUID userId) {
        if (auth.isTenantAdminFromJwt(tenantId)) {
            return;
        }
        Membership m = memberships.findByTenantIdAndUserId(tenantId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Not a tenant member"));
        if (!m.isActive() || !m.isAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tenant admin required");
        }
    }

    /** Org admins can bootstrap team management even without a tenant membership. */
    public void requireTenantAdminOrOrgAdmin(UUID organizationId, UUID tenantId, UUID userId) {
        if (orgRoles.isOrgAdmin(userId, organizationId)) {
            return;
        }
        requireTenantAdmin(tenantId, userId);
    }

    public boolean isTenantAdmin(UUID tenantId, UUID userId) {
        if (auth.isTenantAdminFromJwt(tenantId)) {
            return true;
        }
        return memberships.findByTenantIdAndUserId(tenantId, userId)
                .map(m -> m.isActive() && m.isAdmin())
                .orElse(false);
    }

    public boolean canManageTenant(UUID organizationId, UUID tenantId, UUID userId) {
        return orgRoles.isOrgAdmin(userId, organizationId) || isTenantAdmin(tenantId, userId);
    }

    /** Active membership only (blocked/removed cannot use the workspace). */
    public boolean isActiveMember(UUID tenantId, UUID userId) {
        return memberships.existsByTenantIdAndUserIdAndStatus(tenantId, userId, Membership.STATUS_ACTIVE);
    }

    MembershipResponse toResponse(Membership m, Profile profile) {
        return new MembershipResponse(
                m.getId(),
                m.getTenantId(),
                m.getUserId(),
                m.getOrganizationId(),
                m.getRole(),
                m.getStatus(),
                m.getCreatedAt(),
                m.getDeletedAt(),
                profile != null ? profile.getDisplayName() : null,
                profile != null ? profile.getEmail() : null
        );
    }

    /**
     * Roles: admin | user. Product UI may send "member" which maps to user.
     */
    static String normalizeRole(String role) {
        String r = role == null || role.isBlank() ? Membership.ROLE_USER : role.trim().toLowerCase(Locale.ROOT);
        if ("member".equals(r)) {
            r = Membership.ROLE_USER;
        }
        if (!r.equals(Membership.ROLE_ADMIN) && !r.equals(Membership.ROLE_USER)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "role must be admin or member");
        }
        return r;
    }

    private ActorInfo actorInfo(UUID organizationId, UUID actorUserId) {
        if (actorUserId == null) {
            return new ActorInfo(null, null);
        }
        Profile p = profiles.findByUserIdAndOrganizationId(actorUserId, organizationId).orElse(null);
        return new ActorInfo(displayOf(p), emailOf(p));
    }

    private static String displayOf(Profile p) {
        return p != null ? p.getDisplayName() : null;
    }

    private static String emailOf(Profile p) {
        return p != null ? p.getEmail() : null;
    }

    private record ActorInfo(String displayName, String email) {}
}
