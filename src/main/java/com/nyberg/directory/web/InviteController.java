package com.nyberg.directory.web;

import com.nyberg.directory.dto.DirectoryDtos.*;
import com.nyberg.directory.security.AuthSupport;
import com.nyberg.directory.service.InviteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orgs/{orgId}")
@RequiredArgsConstructor
public class InviteController {

    private final InviteService invites;
    private final AuthSupport auth;

    @PostMapping("/tenants/{tenantId}/invites")
    @ResponseStatus(HttpStatus.CREATED)
    public InviteResponse create(
            @PathVariable UUID orgId,
            @PathVariable UUID tenantId,
            @Valid @RequestBody CreateInviteRequest req) {
        auth.requireOrganizationId(orgId);
        UUID userId = auth.requireUserId();
        return invites.create(orgId, tenantId, userId, req);
    }

    @GetMapping("/tenants/{tenantId}/invites")
    public List<InviteResponse> listTenantPending(
            @PathVariable UUID orgId,
            @PathVariable UUID tenantId) {
        auth.requireOrganizationId(orgId);
        auth.requireUserId();
        return invites.listPendingForTenant(orgId, tenantId);
    }

    @DeleteMapping("/tenants/{tenantId}/invites/{inviteId}")
    public InviteResponse revoke(
            @PathVariable UUID orgId,
            @PathVariable UUID tenantId,
            @PathVariable UUID inviteId) {
        auth.requireOrganizationId(orgId);
        return invites.revoke(orgId, tenantId, inviteId, auth.requireUserId());
    }

    @GetMapping("/invites/pending")
    public List<InviteResponse> myPending(
            @PathVariable UUID orgId,
            @RequestParam String email) {
        auth.requireOrganizationId(orgId);
        auth.requireUserId();
        return invites.listPendingForMe(orgId, email);
    }

    @PostMapping("/invites/{inviteId}/accept")
    public InviteResponse accept(
            @PathVariable UUID orgId,
            @PathVariable UUID inviteId,
            @RequestParam String email) {
        auth.requireOrganizationId(orgId);
        return invites.accept(orgId, inviteId, auth.requireUserId(), email);
    }

    @PostMapping("/invites/{inviteId}/reject")
    public InviteResponse reject(
            @PathVariable UUID orgId,
            @PathVariable UUID inviteId,
            @RequestParam String email) {
        auth.requireOrganizationId(orgId);
        return invites.reject(orgId, inviteId, auth.requireUserId(), email);
    }

    @PostMapping("/invites/claim")
    public ClaimInvitesResponse claim(
            @PathVariable UUID orgId,
            @Valid @RequestBody ClaimInvitesRequest req,
            @RequestParam(defaultValue = "false") boolean autoAccept) {
        auth.requireOrganizationId(orgId);
        return invites.claim(orgId, auth.requireUserId(), req, autoAccept);
    }
}
