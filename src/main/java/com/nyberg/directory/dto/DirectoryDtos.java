package com.nyberg.directory.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class DirectoryDtos {

    private DirectoryDtos() {}

    public record ContactCardRequest(
            String email,
            String phone,
            String website,
            String label
    ) {}

    public record ContactCardResponse(
            UUID id,
            UUID organizationId,
            String email,
            String phone,
            String website,
            String label
    ) {}

    public record AddressCardRequest(
            String line1,
            String line2,
            String city,
            String region,
            String postalCode,
            String country,
            Double latitude,
            Double longitude,
            String label
    ) {}

    public record AddressCardResponse(
            UUID id,
            UUID organizationId,
            String line1,
            String line2,
            String city,
            String region,
            String postalCode,
            String country,
            Double latitude,
            Double longitude,
            String label
    ) {}

    public record OrgProfileRequest(
            String displayName,
            ContactCardRequest contact,
            AddressCardRequest address,
            Map<String, Object> metadata
    ) {}

    public record OrgProfileResponse(
            UUID organizationId,
            String displayName,
            ContactCardResponse contact,
            AddressCardResponse address,
            Map<String, Object> metadata,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record EnsureProfileRequest(
            @NotBlank @Email String email,
            String displayName,
            Map<String, Object> metadata
    ) {}

    public record UpdateProfileRequest(
            String email,
            String displayName,
            ContactCardRequest contact,
            AddressCardRequest address,
            Map<String, Object> metadata
    ) {}

    public record ProfileResponse(
            UUID id,
            UUID userId,
            UUID organizationId,
            String email,
            String displayName,
            String orgRole,
            ContactCardResponse contact,
            AddressCardResponse address,
            Map<String, Object> metadata,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record CreateTenantRequest(
            @NotBlank String name,
            String slug,
            String description
    ) {}

    /** Bootstrap: create tenant with a specific IAM tenant id if missing; add caller as admin. */
    public record EnsureTenantRequest(
            @NotNull UUID id,
            @NotBlank String name,
            String slug,
            String description
    ) {}

    public record UpdateTenantRequest(
            String name,
            String slug,
            String description
    ) {}

    public record TenantResponse(
            UUID id,
            UUID organizationId,
            String name,
            String slug,
            String description,
            UUID createdBy,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record AddMemberRequest(
            @NotNull UUID userId,
            String role
    ) {}

    public record UpdateMemberRoleRequest(
            @NotBlank String role
    ) {}

    public record MembershipResponse(
            UUID id,
            UUID tenantId,
            UUID userId,
            UUID organizationId,
            String role,
            Instant createdAt
    ) {}

    public record CreateInviteRequest(
            @NotBlank @Email String email,
            String role
    ) {}

    public record InviteResponse(
            UUID id,
            UUID organizationId,
            UUID tenantId,
            String email,
            String role,
            String token,
            String status,
            UUID invitedBy,
            Instant expiresAt,
            Instant respondedAt,
            Instant createdAt
    ) {}

    public record ClaimInvitesRequest(
            @NotBlank @Email String email
    ) {}

    public record ClaimInvitesResponse(
            int matched,
            int accepted
    ) {}

    // ── Guided / system tasks ─────────────────────────────────────────────────

    public record CreateGuidedTaskRequest(
            /** Required when using a service token; ignored for user JWT (always self). */
            UUID subjectUserId,
            @NotBlank String type,
            String status,
            String priority,
            String title,
            String body,
            String actionUrl,
            /** Null/blank = Home only; otherwise path prefix where the task is shown. */
            String displayRoute,
            /** user (default) | system — who may dismiss/complete from the product UI. */
            String dismissal,
            Map<String, Object> payload,
            String source,
            String dedupeKey,
            UUID tenantId
    ) {}

    public record UpdateGuidedTaskStatusRequest(
            @NotBlank String status
    ) {}

    public record GuidedTaskResponse(
            UUID id,
            UUID organizationId,
            UUID tenantId,
            UUID subjectUserId,
            String type,
            String status,
            String priority,
            String title,
            String body,
            String actionUrl,
            String displayRoute,
            String dismissal,
            Map<String, Object> payload,
            String source,
            String dedupeKey,
            Instant createdAt,
            Instant updatedAt,
            Instant completedAt,
            Instant dismissedAt
    ) {}
}
