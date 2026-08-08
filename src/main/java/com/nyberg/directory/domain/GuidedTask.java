package com.nyberg.directory.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "guided_tasks", schema = "directory")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GuidedTask {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "subject_user_id", nullable = false)
    private UUID subjectUserId;

    @Column(nullable = false, length = 128)
    private String type;

    @Column(nullable = false, length = 64)
    private String status;

    @Column(nullable = false, length = 32)
    private String priority;

    @Column(length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String body;

    @Column(name = "action_url", columnDefinition = "TEXT")
    private String actionUrl;

    /**
     * Where the task is shown in the product UI. Null/blank = Home (/app/me) only.
     * When set, matched as a path prefix (e.g. /app/workspace).
     */
    @Column(name = "display_route", length = 255)
    private String displayRoute;

    /**
     * Who may dismiss/complete from the product UI: {@code user} (default) or {@code system}.
     */
    @Column(nullable = false, length = 32)
    @Builder.Default
    private String dismissal = "user";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> payload;

    @Column(length = 64)
    private String source;

    @Column(name = "dedupe_key", length = 128)
    private String dedupeKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "dismissed_at")
    private Instant dismissedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (status == null || status.isBlank()) status = "open";
        if (priority == null || priority.isBlank()) priority = "normal";
        if (dismissal == null || dismissal.isBlank()) dismissal = "user";
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
