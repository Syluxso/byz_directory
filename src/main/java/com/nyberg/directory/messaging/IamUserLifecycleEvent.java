package com.nyberg.directory.messaging;

import java.time.Instant;
import java.util.UUID;

/**
 * Payload for {@code byz.iam.user} (subset used by directory).
 * Compatible with byz-iam {@code UserLifecycleEvent} JSON.
 */
public record IamUserLifecycleEvent(
        UUID eventId,
        String type,
        Instant occurredAt,
        UUID organizationId,
        UUID tenantId,
        UUID userId,
        String email,
        String displayName,
        String resetUrl,
        String provider
) {
    public static final String TYPE_USER_REGISTERED = "user.registered";
    public static final String TYPE_USER_AUTHENTICATED = "user.authenticated";
}
