package com.nyberg.directory.messaging;

import java.time.Instant;
import java.util.UUID;

/**
 * JSON payload for {@code byz.directory.membership}.
 * Domain events for invites and membership lifecycle (product rules live in managed-api).
 */
public record MembershipLifecycleEvent(
        UUID eventId,
        String type,
        Instant occurredAt,
        UUID organizationId,
        UUID tenantId,
        String tenantName,
        String tenantSlug,
        /** Actor who performed the action (inviter, admin, or self for leave). */
        UUID actorUserId,
        String actorDisplayName,
        String actorEmail,
        /** Target member or invitee user id when known. */
        UUID targetUserId,
        String targetEmail,
        String targetDisplayName,
        String role,
        String previousRole,
        String status,
        UUID inviteId
) {
    public static final String TYPE_INVITE_CREATED = "invite.created";
    public static final String TYPE_INVITE_ACCEPTED = "invite.accepted";
    public static final String TYPE_INVITE_REJECTED = "invite.rejected";
    public static final String TYPE_INVITE_REVOKED = "invite.revoked";
    public static final String TYPE_INVITE_RESENT = "invite.resent";
    public static final String TYPE_MEMBERSHIP_JOINED = "membership.joined";
    public static final String TYPE_MEMBERSHIP_ROLE_CHANGED = "membership.role_changed";
    public static final String TYPE_MEMBERSHIP_REMOVED = "membership.removed";
    public static final String TYPE_MEMBERSHIP_LEFT = "membership.left";
    public static final String TYPE_MEMBERSHIP_BLOCKED = "membership.blocked";
    public static final String TYPE_MEMBERSHIP_UNBLOCKED = "membership.unblocked";

    public static MembershipLifecycleEvent of(
            String type,
            UUID organizationId,
            UUID tenantId,
            String tenantName,
            String tenantSlug,
            UUID actorUserId,
            String actorDisplayName,
            String actorEmail,
            UUID targetUserId,
            String targetEmail,
            String targetDisplayName,
            String role,
            String previousRole,
            String status,
            UUID inviteId
    ) {
        return new MembershipLifecycleEvent(
                UUID.randomUUID(),
                type,
                Instant.now(),
                organizationId,
                tenantId,
                tenantName,
                tenantSlug,
                actorUserId,
                actorDisplayName,
                actorEmail,
                targetUserId,
                targetEmail,
                targetDisplayName,
                role,
                previousRole,
                status,
                inviteId
        );
    }
}
