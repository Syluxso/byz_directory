package com.nyberg.directory.service;

import com.nyberg.directory.domain.Invite;
import com.nyberg.directory.domain.Membership;
import com.nyberg.directory.dto.DirectoryDtos.*;
import com.nyberg.directory.repository.InviteRepository;
import com.nyberg.directory.repository.MembershipRepository;
import com.nyberg.directory.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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

    @Transactional
    public InviteResponse create(UUID organizationId, UUID tenantId, UUID invitedBy, CreateInviteRequest req) {
        tenants.requireTenant(organizationId, tenantId);
        membershipService.requireTenantAdmin(tenantId, invitedBy);

        String email = normalizeEmail(req.email());
        String role = MembershipService.normalizeRole(req.role());

        Invite invite = invites.save(Invite.builder()
                .organizationId(organizationId)
                .tenantId(tenantId)
                .email(email)
                .role(role)
                .token(newToken())
                .status("pending")
                .invitedBy(invitedBy)
                .expiresAt(Instant.now().plus(14, ChronoUnit.DAYS))
                .build());
        return toResponse(invite);
    }

    @Transactional(readOnly = true)
    public List<InviteResponse> listPendingForTenant(UUID organizationId, UUID tenantId) {
        tenants.requireTenant(organizationId, tenantId);
        return invites.findByTenantIdAndStatus(tenantId, "pending").stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<InviteResponse> listPendingForMe(UUID organizationId, String email) {
        return invites.findPendingByOrgAndEmail(organizationId, normalizeEmail(email)).stream()
                .filter(this::stillValid)
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public InviteResponse accept(UUID organizationId, UUID inviteId, UUID userId, String email) {
        Invite invite = requirePendingInvite(organizationId, inviteId);
        assertEmailMatch(invite, email);
        expireIfNeeded(invite);
        if (!"pending".equals(invite.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Invite is " + invite.getStatus());
        }

        if (!memberships.existsByTenantIdAndUserId(invite.getTenantId(), userId)) {
            memberships.save(Membership.builder()
                    .tenantId(invite.getTenantId())
                    .userId(userId)
                    .organizationId(organizationId)
                    .role(invite.getRole())
                    .build());
        }

        invite.setStatus("accepted");
        invite.setRespondedAt(Instant.now());
        return toResponse(invites.save(invite));
    }

    @Transactional
    public InviteResponse reject(UUID organizationId, UUID inviteId, UUID userId, String email) {
        Invite invite = requirePendingInvite(organizationId, inviteId);
        assertEmailMatch(invite, email);
        expireIfNeeded(invite);
        if (!"pending".equals(invite.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Invite is " + invite.getStatus());
        }
        invite.setStatus("rejected");
        invite.setRespondedAt(Instant.now());
        return toResponse(invites.save(invite));
    }

    @Transactional
    public InviteResponse revoke(UUID organizationId, UUID tenantId, UUID inviteId, UUID actorUserId) {
        tenants.requireTenant(organizationId, tenantId);
        membershipService.requireTenantAdmin(tenantId, actorUserId);
        Invite invite = invites.findById(inviteId)
                .filter(i -> i.getTenantId().equals(tenantId) && i.getOrganizationId().equals(organizationId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invite not found"));
        if (!"pending".equals(invite.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only pending invites can be revoked");
        }
        invite.setStatus("revoked");
        invite.setRespondedAt(Instant.now());
        return toResponse(invites.save(invite));
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

        List<Invite> pending = invites.findPendingByOrgAndEmail(organizationId, email).stream()
                .filter(this::stillValid)
                .toList();

        int accepted = 0;
        if (autoAccept) {
            for (Invite invite : pending) {
                if (!memberships.existsByTenantIdAndUserId(invite.getTenantId(), userId)) {
                    memberships.save(Membership.builder()
                            .tenantId(invite.getTenantId())
                            .userId(userId)
                            .organizationId(organizationId)
                            .role(invite.getRole())
                            .build());
                }
                invite.setStatus("accepted");
                invite.setRespondedAt(Instant.now());
                invites.save(invite);
                accepted++;
            }
        }

        return new ClaimInvitesResponse(pending.size(), accepted);
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

    private boolean stillValid(Invite invite) {
        if (invite.getExpiresAt() != null && invite.getExpiresAt().isBefore(Instant.now())) {
            return false;
        }
        return "pending".equals(invite.getStatus());
    }

    private void expireIfNeeded(Invite invite) {
        if (invite.getExpiresAt() != null && invite.getExpiresAt().isBefore(Instant.now())
                && "pending".equals(invite.getStatus())) {
            invite.setStatus("expired");
            invites.save(invite);
        }
    }

    private InviteResponse toResponse(Invite i) {
        return new InviteResponse(
                i.getId(), i.getOrganizationId(), i.getTenantId(), i.getEmail(), i.getRole(),
                i.getToken(), i.getStatus(), i.getInvitedBy(), i.getExpiresAt(), i.getRespondedAt(), i.getCreatedAt());
    }

    private static String newToken() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
