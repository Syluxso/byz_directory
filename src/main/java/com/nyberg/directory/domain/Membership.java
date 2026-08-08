package com.nyberg.directory.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "memberships", schema = "directory")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Membership {

    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_BLOCKED = "blocked";
    public static final String STATUS_REMOVED = "removed";

    public static final String ROLE_ADMIN = "admin";
    public static final String ROLE_USER = "user";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(nullable = false)
    private String role;

    /** active | blocked | removed */
    @Column(nullable = false)
    private String status;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (role == null) role = ROLE_USER;
        if (status == null) status = STATUS_ACTIVE;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public boolean isActive() {
        return STATUS_ACTIVE.equals(status);
    }

    public boolean isBlocked() {
        return STATUS_BLOCKED.equals(status);
    }

    public boolean isRemoved() {
        return STATUS_REMOVED.equals(status);
    }

    public boolean isAdmin() {
        return ROLE_ADMIN.equals(role);
    }
}
