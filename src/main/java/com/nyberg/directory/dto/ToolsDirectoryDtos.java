package com.nyberg.directory.dto;

import java.util.List;

/**
 * Public-facing shapes for agent tools only — no internal UUIDs, secrets, or admin metadata.
 */
public final class ToolsDirectoryDtos {

    private ToolsDirectoryDtos() {}

    public record PublicContact(
            String email,
            String phone,
            String website,
            String label
    ) {}

    public record PublicAddress(
            String line1,
            String line2,
            String city,
            String region,
            String postalCode,
            String country,
            String label
    ) {}

    public record PublicPerson(
            String displayName,
            String email,
            String orgRole,
            PublicContact contact,
            PublicAddress address
    ) {}

    public record PublicOrganization(
            String displayName,
            PublicContact contact,
            PublicAddress address
    ) {}

    public record PublicTenantMembership(
            String name,
            String slug,
            String description,
            String role
    ) {}

    /**
     * Caller context for Pax / agent tools.
     * Scope is always the chat user + org (resolved server-side).
     *
     * <p>{@code profileFound} is false when no directory person row exists yet;
     * {@code person} is null in that case. Organization and tenants are still returned
     * when available (session ids are enough).
     */
    public record ToolsWhoamiResponse(
            boolean profileFound,
            PublicPerson person,
            PublicOrganization organization,
            List<PublicTenantMembership> tenants
    ) {}
}
