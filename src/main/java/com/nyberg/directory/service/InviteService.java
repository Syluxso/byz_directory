package com.nyberg.directory.service;

import com.nyberg.directory.domain.DirTenant;
import com.nyberg.directory.domain.Invite;
import com.nyberg.directory.domain.Membership;
import com.nyberg.directory.domain.Profile;
import com.nyberg.directory.dto.DirectoryDtos.*;
import com.nyberg.directory.messaging.MembershipLifecycleApplicationEvent;
import com.nyberg.directory.messaging.MembershipLifecycleEvent;
import com.nyberg.directory.repository.InviteRepository;
import com.nyberg.directory.repository.MembershipRepository;
import com.nyberg.directory.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InviteService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final InviteRepository invites;
    private final MembershipRepository memberships;
    private final ProfileRepository profiles;
    private final TenantService tenants;
    private final MembershipService membershipService;
    private final ApplicationEventPublisher events;

    @Transactional
    public InviteResponse create(UUID organizationId, UUID tenantId, UUID invitedBy, CreateInviteRequest req) {
        DirTenant tenant = tenants.requireTenant(organizationId, tenantId);
        membershipService.requireTenantAdminOrOrgAdmin(organizationId, tenantId, invitedBy);

        String email = normalizeEmail(req.email());
        String role = MembershipService.normalizeRole(req.role());

        // If the email maps to an active member, reject.
        profiles.findByOrganizationIdAndEmailIgnoreCase(organizationId, email).ifPresent(p -> {
            if (memberships.existsByTenantIdAndUserIdAndStatus(
                    tenantId, p.getUserId(), Membership.STATUS_ACTIVE)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "User is already a member");
            }
            if (memberships.existsByTenantIdAndUserIdAndStatus(
                    tenantId, p.getUserId(), Membership.STATUS_BLOCKED)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "User is blocked; unblock first");
            }
        });

        // Reuse existing pending invite for same email+tenant (update role/token).
        Invite invite = invites.findByTenantIdAndStatus(tenantId, "pending").stream()
                .filter(i -> i.getEmail().equalsIgnoreCase(email))
                .findFirst()
                .orElse(null);

        if (invite != null) {
            invite.setRole(role);
            invite.setToken(newToken());
            invite.setInvitedBy(invitedBy);
            invite.setExpiresAt(null);
            invite = invites.save(invite);
            publishInviteEvent(MembershipLifecycleEvent.TYPE_INVITE_RESENT, organizationId, tenant,
                    invitedBy, invite, null);
            return toResponse(invite, tenant.getName());
        }

        invite = invites.save(Invite.builder()
                .organizationId(organizationId)
                .tenantId(tenantId)
                .email(email)
                .role(role)
                .token(newToken())
                .status("pending")
                .invitedBy(invitedBy)
                .expiresAt(null)
                .build());
        publishInviteEvent(MembershipLifecycleEvent.TYPE_INVITE_CREATED, organizationId, tenant,
                invitedBy, invite, null);
        return toResponse(invite, tenant.getName());
    }

    @Transactional
    public InviteResponse resend(UUID organizationId, UUID tenantId, UUID inviteId, UUID actorUserId) {
        DirTenant tenant = tenants.requireTenant(organizationId, tenantId);
        membershipService.requireTenantAdminOrOrgAdmin(organizationId, tenantId, actorUserId);
        Invite invite = invites.findById(inviteId)
                .filter(i -> i.getTenantId().equals(tenantId) && i.getOrganizationId().equals(organizationId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invite not found"));
        if (!"pending".equals(invite.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only pending invites can be resent");
        }
        invite.setToken(newToken());
        invite.setExpiresAt(null);
        invite.setInvitedBy(actorUserId);
        invite = invites.save(invite);
        publishInviteEvent(MembershipLifecycleEvent.TYPE_INVITE_RESENT, organizationId, tenant,
                actorUserId, invite, null);
        return toResponse(invite, tenant.getName());
    }

    @Transactional(readOnly = true)
    public List<InviteResponse> listPendingForTenant(UUID organizationId, UUID tenantId) {
        DirTenant tenant = tenants.requireTenant(organizationId, tenantId);
        return invites.findByTenantIdAndStatus(tenantId, "pending").stream()
                .map(i -> toResponse(i, tenant.getName()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<InviteResponse> listPendingForMe(UUID organizationId, String email) {
        return invites.findPendingByOrgAndEmail(organizationId, normalizeEmail(email)).stream()
                .map(i -> {
                    String tenantName = null;
                    try {
                        tenantName = tenants.requireTenant(organizationId, i.getTenantId()).getName();
                    } catch (Exception ignored) {
                        // tenant may be gone
                    }
                    return toResponse(i, tenantName);
                })
                .toList();
    }

    @Transactional
    public InviteResponse accept(UUID organizationId, UUID inviteId, UUID userId, String email) {
        Invite invite = requirePendingInvite(organizationId, inviteId);
        assertEmailMatch(invite, email);
        if (!"pending".equals(invite.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Invite is " + invite.getStatus());
        }

        DirTenant tenant = tenants.requireTenant(organizationId, invite.getTenantId());
        Membership m = membershipService.ensureActiveMembership(
                organizationId, invite.getTenantId(), userId, invite.getRole());
        // tenant.member_joined keeps create-workspace guided tasks in sync; product notify uses invite.accepted.
        membershipService.publishTenantMemberJoinedOnly(organizationId, tenant, m.getUserId());

        invite.setStatus("accepted");
        invite.setRespondedAt(Instant.now());
        Invite saved = invites.save(invite);

        Profile target = profiles.findByUserIdAndOrganizationId(userId, organizationId).orElse(null);
        Profile actor = invite.getInvitedBy() != null
                ? profiles.findByUserIdAndOrganizationId(invite.getInvitedBy(), organizationId).orElse(null)
                : null;
        events.publishEvent(new MembershipLifecycleApplicationEvent(this, MembershipLifecycleEvent.of(
                MembershipLifecycleEvent.TYPE_INVITE_ACCEPTED,
                organizationId, tenant.getId(), tenant.getName(), tenant.getSlug(),
                invite.getInvitedBy(),
                actor != null ? actor.getDisplayName() : null,
                actor != null ? actor.getEmail() : null,
                userId,
                emailOf(target, email),
                target != null ? target.getDisplayName() : null,
                invite.getRole(), null, m.getStatus(), invite.getId()
        )));
        return toResponse(saved, tenant.getName());
    }

    @Transactional
    public InviteResponse reject(UUID organizationId, UUID inviteId, UUID userId, String email) {
        Invite invite = requirePendingInvite(organizationId, inviteId);
        assertEmailMatch(invite, email);
        if (!"pending".equals(invite.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Invite is " + invite.getStatus());
        }
        DirTenant tenant = tenants.requireTenant(organizationId, invite.getTenantId());
        invite.setStatus("rejected");
        invite.setRespondedAt(Instant.now());
        Invite saved = invites.save(invite);

        Profile target = profiles.findByUserIdAndOrganizationId(userId, organizationId).orElse(null);
        Profile actor = invite.getInvitedBy() != null
                ? profiles.findByUserIdAndOrganizationId(invite.getInvitedBy(), organizationId).orElse(null)
                : null;
        events.publishEvent(new MembershipLifecycleApplicationEvent(this, MembershipLifecycleEvent.of(
                MembershipLifecycleEvent.TYPE_INVITE_REJECTED,
                organizationId, tenant.getId(), tenant.getName(), tenant.getSlug(),
                invite.getInvitedBy(),
                actor != null ? actor.getDisplayName() : null,
                actor != null ? actor.getEmail() : null,
                userId,
                emailOf(target, email),
                target != null ? target.getDisplayName() : null,
                invite.getRole(), null, null, invite.getId()
        )));
        return toResponse(saved, tenant.getName());
    }

    @Transactional
    public InviteResponse revoke(UUID organizationId, UUID tenantId, UUID inviteId, UUID actorUserId) {
        DirTenant tenant = tenants.requireTenant(organizationId, tenantId);
        membershipService.requireTenantAdminOrOrgAdmin(organizationId, tenantId, actorUserId);
        Invite invite = invites.findById(inviteId)
                .filter(i -> i.getTenantId().equals(tenantId) && i.getOrganizationId().equals(organizationId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invite not found"));
        if (!"pending".equals(invite.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only pending invites can be revoked");
        }
        invite.setStatus("revoked");
        invite.setRespondedAt(Instant.now());
        Invite saved = invites.save(invite);
        publishInviteEvent(MembershipLifecycleEvent.TYPE_INVITE_REVOKED, organizationId, tenant,
                actorUserId, saved, null);
        return toResponse(saved, tenant.getName());
    }

    /**
     * After IAM register / first login: match pending invites for org+email and leave them
     * for the user to accept (does not auto-accept). Returns matched count.
     * Optionally auto-accepts when autoAccept=true.
     */
    @Transactional
    public ClaimInvitesResponse claim(UUID organizationId, UUID userId, ClaimInvitesRequest req, boolean autoAccept) {
        String email = normalizeEmail(req.email());
        profiles.findByUserIdAndOrganizationId(userId, organizationId).ifPresent(p -> {
            if (!p.getEmail().equalsIgnoreCase(email)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Claim email must match directory profile email");
            }
        });

        List<Invite> pending = invites.findPendingByOrgAndEmail(organizationId, email);

        int accepted = 0;
        if (autoAccept) {
            for (Invite invite : pending) {
                DirTenant tenant = tenants.requireTenant(organizationId, invite.getTenantId());
                Membership m = membershipService.ensureActiveMembership(
                        organizationId, invite.getTenantId(), userId, invite.getRole());
                membershipService.publishTenantMemberJoinedOnly(organizationId, tenant, m.getUserId());
                invite.setStatus("accepted");
                invite.setRespondedAt(Instant.now());
                invites.save(invite);
                accepted++;
                Profile target = profiles.findByUserIdAndOrganizationId(userId, organizationId).orElse(null);
                Profile actor = invite.getInvitedBy() != null
                        ? profiles.findByUserIdAndOrganizationId(invite.getInvitedBy(), organizationId).orElse(null)
                        : null;
                events.publishEvent(new MembershipLifecycleApplicationEvent(this, MembershipLifecycleEvent.of(
                        MembershipLifecycleEvent.TYPE_INVITE_ACCEPTED,
                        organizationId, tenant.getId(), tenant.getName(), tenant.getSlug(),
                        invite.getInvitedBy(),
                        actor != null ? actor.getDisplayName() : null,
                        actor != null ? actor.getEmail() : null,
                        userId,
                        emailOf(target, email),
                        target != null ? target.getDisplayName() : null,
                        invite.getRole(), null, m.getStatus(), invite.getId()
                )));
            }
        }

        return new ClaimInvitesResponse(pending.size(), accepted);
    }

    private void publishInviteEvent(
            String type,
            UUID organizationId,
            DirTenant tenant,
            UUID actorUserId,
            Invite invite,
            UUID targetUserId
    ) {
        Profile actor = actorUserId != null
                ? profiles.findByUserIdAndOrganizationId(actorUserId, organizationId).orElse(null)
                : null;
        Profile target = targetUserId != null
                ? profiles.findByUserIdAndOrganizationId(targetUserId, organizationId).orElse(null)
                : profiles.findByOrganizationIdAndEmailIgnoreCase(organizationId, invite.getEmail()).orElse(null);
        events.publishEvent(new MembershipLifecycleApplicationEvent(this, MembershipLifecycleEvent.of(
                type,
                organizationId, tenant.getId(), tenant.getName(), tenant.getSlug(),
                actorUserId,
                actor != null ? actor.getDisplayName() : null,
                actor != null ? actor.getEmail() : null,
                target != null ? target.getUserId() : targetUserId,
                invite.getEmail(),
                target != null ? target.getDisplayName() : null,
                invite.getRole(), null, null, invite.getId()
        )));
    }

    private Invite requirePendingInvite(UUID organizationId, UUID inviteId) {
        Invite invite = invites.findById(inviteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invite not found"));
        if (!invite.getOrganizationId().equals(organizationId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Organization mismatch");
        }
        return invite;
    }

    private void assertEmailMatch(Invite invite, String email) {
        if (!invite.getEmail().equalsIgnoreCase(normalizeEmail(email))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invite email does not match");
        }
    }

    private InviteResponse toResponse(Invite i, String tenantName) {
        return new InviteResponse(
                i.getId(), i.getOrganizationId(), i.getTenantId(), i.getEmail(), i.getRole(),
                i.getToken(), i.getStatus(), i.getInvitedBy(), i.getExpiresAt(), i.getRespondedAt(),
                i.getCreatedAt(), tenantName);
    }

    private static String newToken() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String emailOf(Profile p, String fallback) {
        if (p != null && p.getEmail() != null) return p.getEmail();
        return fallback;
    }
}
