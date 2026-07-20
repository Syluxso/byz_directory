package com.nyberg.directory.web;

import com.nyberg.directory.dto.DirectoryDtos.*;
import com.nyberg.directory.security.AuthSupport;
import com.nyberg.directory.service.MembershipService;
import com.nyberg.directory.service.OrgRoleService;
import com.nyberg.directory.service.TenantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orgs/{orgId}/tenants")
@RequiredArgsConstructor
public class TenantController {

    private final TenantService tenants;
    private final MembershipService memberships;
    private final OrgRoleService orgRoles;
    private final AuthSupport auth;

    @GetMapping
    public List<TenantResponse> list(@PathVariable UUID orgId) {
        auth.requireOrganizationId(orgId);
        auth.requireUserId();
        return tenants.list(orgId);
    }

    @GetMapping("/{tenantId}")
    public TenantResponse get(@PathVariable UUID orgId, @PathVariable UUID tenantId) {
        auth.requireOrganizationId(orgId);
        auth.requireUserId();
        return tenants.get(orgId, tenantId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TenantResponse create(@PathVariable UUID orgId, @Valid @RequestBody CreateTenantRequest req) {
        auth.requireOrganizationId(orgId);
        UUID userId = auth.requireUserId();
        orgRoles.requireOrgAdmin(userId, orgId);
        return tenants.create(orgId, userId, req);
    }

    /**
     * Signup bootstrap: JWT tenant_id must match body.id. Creates the directory tenant if missing
     * and ensures the caller is a member (admin if first).
     */
    @PostMapping("/ensure")
    public TenantResponse ensure(@PathVariable UUID orgId, @Valid @RequestBody EnsureTenantRequest req) {
        auth.requireOrganizationId(orgId);
        UUID userId = auth.requireUserId();
        UUID jwtTenant = auth.requireTenantId();
        if (!jwtTenant.equals(req.id())) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.FORBIDDEN, "tenant id must match token tenant_id");
        }
        return tenants.ensure(orgId, userId, req);
    }

    @PutMapping("/{tenantId}")
    public TenantResponse update(
            @PathVariable UUID orgId,
            @PathVariable UUID tenantId,
            @RequestBody UpdateTenantRequest req) {
        auth.requireOrganizationId(orgId);
        orgRoles.requireOrgAdmin(auth.requireUserId(), orgId);
        return tenants.update(orgId, tenantId, req);
    }

    @DeleteMapping("/{tenantId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID orgId, @PathVariable UUID tenantId) {
        auth.requireOrganizationId(orgId);
        orgRoles.requireOrgAdmin(auth.requireUserId(), orgId);
        tenants.softDelete(orgId, tenantId);
    }

    @GetMapping("/{tenantId}/members")
    public List<MembershipResponse> listMembers(@PathVariable UUID orgId, @PathVariable UUID tenantId) {
        auth.requireOrganizationId(orgId);
        auth.requireUserId();
        return memberships.listMembers(orgId, tenantId);
    }

    @PostMapping("/{tenantId}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public MembershipResponse addMember(
            @PathVariable UUID orgId,
            @PathVariable UUID tenantId,
            @Valid @RequestBody AddMemberRequest req) {
        auth.requireOrganizationId(orgId);
        UUID actor = auth.requireUserId();
        return memberships.addMember(orgId, tenantId, actor, req);
    }

    @PatchMapping("/{tenantId}/members/{userId}")
    public MembershipResponse updateRole(
            @PathVariable UUID orgId,
            @PathVariable UUID tenantId,
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateMemberRoleRequest req) {
        auth.requireOrganizationId(orgId);
        UUID actor = auth.requireUserId();
        return memberships.updateRole(orgId, tenantId, userId, actor, req);
    }

    @DeleteMapping("/{tenantId}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(
            @PathVariable UUID orgId,
            @PathVariable UUID tenantId,
            @PathVariable UUID userId) {
        auth.requireOrganizationId(orgId);
        UUID actor = auth.requireUserId();
        memberships.removeMember(orgId, tenantId, userId, actor);
    }
}
