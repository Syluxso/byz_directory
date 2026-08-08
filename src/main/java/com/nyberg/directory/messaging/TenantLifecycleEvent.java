package com.nyberg.directory.messaging;

import java.time.Instant;
import java.util.UUID;

/**
 * JSON payload for {@code byz.directory.tenant}.
 * Types: {@link #TYPE_TENANT_CREATED}, {@link #TYPE_MEMBER_JOINED}.
 */
public record TenantLifecycleEvent(
        UUID eventId,
        String type,
        Instant occurredAt,
        UUID organizationId,
        UUID tenantId,
        /** User who created the workspace or joined. */
        UUID userId,
        String tenantName,
        String tenantSlug
) {
    public static final String TYPE_TENANT_CREATED = "tenant.created";
    public static final String TYPE_MEMBER_JOINED = "tenant.member_joined";

    public static TenantLifecycleEvent tenantCreated(
            UUID organizationId,
            UUID tenantId,
            UUID creatorUserId,
            String tenantName,
            String tenantSlug
    ) {
        return new TenantLifecycleEvent(
                UUID.randomUUID(),
                TYPE_TENANT_CREATED,
                Instant.now(),
                organizationId,
                tenantId,
                creatorUserId,
                tenantName,
                tenantSlug
        );
    }

    public static TenantLifecycleEvent memberJoined(
            UUID organizationId,
            UUID tenantId,
            UUID userId,
            String tenantName,
            String tenantSlug
    ) {
        return new TenantLifecycleEvent(
                UUID.randomUUID(),
                TYPE_MEMBER_JOINED,
                Instant.now(),
                organizationId,
                tenantId,
                userId,
                tenantName,
                tenantSlug
        );
    }
}
