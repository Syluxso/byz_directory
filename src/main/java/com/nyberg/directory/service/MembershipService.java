package com.nyberg.directory.service;

import com.nyberg.directory.client.IamRoleClient;
import com.nyberg.directory.domain.Membership;
import com.nyberg.directory.dto.DirectoryDtos.*;
import com.nyberg.directory.repository.MembershipRepository;
import com.nyberg.directory.security.AuthSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MembershipService {

    private final MembershipRepository memberships;
    private final TenantService tenants;
    private final OrgRoleService orgRoles;
    private final AuthSupport auth;
    private final IamRoleClient iamRoles;

    @Transactional(readOnly = true)
    public List<MembershipResponse> listMembers(UUID organizationId, UUID tenantId) {
        tenants.requireTenant(organizationId, tenantId);
        return memberships.findByTenantId(tenantId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<MembershipResponse> listMyMemberships(UUID userId, UUID organizationId) {
        return memberships.findByUserIdAndOrganizationId(userId, organizationId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public MembershipResponse addMember(UUID organizationId, UUID tenantId, UUID actorUserId, AddMemberRequest req) {
        tenants.requireTenant(organizationId, tenantId);
        requireTenantAdminOrOrgAdmin(organizationId, tenantId, actorUserId);
        String role = normalizeRole(req.role());
        if (memberships.existsByTenantIdAndUserId(tenantId, req.userId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "User already a member");
        }
        Membership m = memberships.save(Membership.builder()
                .tenantId(tenantId)
                .userId(req.userId())
                .organizationId(organizationId)
                .role(role)
                .build());
        iamRoles.syncTenantRole(organizationId, tenantId, req.userId(), role);
        return toResponse(m);
    }

    @Transactional
    public MembershipResponse updateRole(UUID organizationId, UUID tenantId, UUID targetUserId, UUID actorUserId, UpdateMemberRoleRequest req) {
        tenants.requireTenant(organizationId, tenantId);
        requireTenantAdminOrOrgAdmin(organizationId, tenantId, actorUserId);
        Membership m = memberships.findByTenantIdAndUserId(tenantId, targetUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Membership not found"));
        m.setRole(normalizeRole(req.role()));
        Membership saved = memberships.save(m);
        iamRoles.syncTenantRole(organizationId, tenantId, targetUserId, saved.getRole());
        return toResponse(saved);
    }

    @Transactional
    public void removeMember(UUID organizationId, UUID tenantId, UUID targetUserId, UUID actorUserId) {
        tenants.requireTenant(organizationId, tenantId);
        requireTenantAdminOrOrgAdmin(organizationId, tenantId, actorUserId);
        if (!memberships.existsByTenantIdAndUserId(tenantId, targetUserId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Membership not found");
        }
        memberships.deleteByTenantIdAndUserId(tenantId, targetUserId);
        iamRoles.revokeTenantRoles(organizationId, tenantId, targetUserId);
    }

    public void requireTenantAdmin(UUID tenantId, UUID userId) {
        if (auth.isTenantAdminFromJwt(tenantId)) {
            return;
        }
        Membership m = memberships.findByTenantIdAndUserId(tenantId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Not a tenant member"));
        if (!"admin".equals(m.getRole())) {
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
                .map(m -> "admin".equals(m.getRole()))
                .orElse(false);
    }

    public boolean canManageTenant(UUID organizationId, UUID tenantId, UUID userId) {
        return orgRoles.isOrgAdmin(userId, organizationId) || isTenantAdmin(tenantId, userId);
    }

    MembershipResponse toResponse(Membership m) {
        return new MembershipResponse(m.getId(), m.getTenantId(), m.getUserId(), m.getOrganizationId(), m.getRole(), m.getCreatedAt());
    }

    static String normalizeRole(String role) {
        String r = role == null || role.isBlank() ? "user" : role.trim().toLowerCase(Locale.ROOT);
        if (!r.equals("admin") && !r.equals("user")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "role must be admin or user");
        }
        return r;
    }
}
