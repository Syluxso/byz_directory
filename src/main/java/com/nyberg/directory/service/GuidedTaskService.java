package com.nyberg.directory.service;

import com.nyberg.directory.domain.GuidedTask;
import com.nyberg.directory.dto.DirectoryDtos.CreateGuidedTaskRequest;
import com.nyberg.directory.dto.DirectoryDtos.GuidedTaskResponse;
import com.nyberg.directory.repository.GuidedTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GuidedTaskService {

    private final GuidedTaskRepository repo;

    @Transactional(readOnly = true)
    public List<GuidedTaskResponse> listForSubject(UUID orgId, UUID subjectUserId, String status) {
        List<GuidedTask> rows;
        if (status != null && !status.isBlank()) {
            rows = repo.findByOrganizationIdAndSubjectUserIdAndStatusIgnoreCaseOrderByCreatedAtDesc(
                    orgId, subjectUserId, normalizeStatus(status));
        } else {
            rows = repo.findByOrganizationIdAndSubjectUserIdOrderByCreatedAtDesc(orgId, subjectUserId);
        }
        return rows.stream().map(this::toResponse).toList();
    }

    /**
     * Create or return existing open task for the same org+subject+type+dedupeKey.
     */
    @Transactional
    public GuidedTaskResponse create(UUID orgId, UUID subjectUserId, CreateGuidedTaskRequest req) {
        String type = req.type().trim();
        if (type.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "type is required");
        }
        String dedupe = blankToNull(req.dedupeKey());
        String status = req.status() != null && !req.status().isBlank()
                ? normalizeStatus(req.status())
                : "open";

        if ("open".equals(status)) {
            var existing = repo.findOpenDedupe(orgId, subjectUserId, type, dedupe);
            if (existing.isPresent()) {
                return toResponse(existing.get());
            }
        }

        GuidedTask task = GuidedTask.builder()
                .organizationId(orgId)
                .tenantId(req.tenantId())
                .subjectUserId(subjectUserId)
                .type(type)
                .status(status)
                .priority(normalizePriority(req.priority()))
                .title(blankToNull(req.title()))
                .body(blankToNull(req.body()))
                .actionUrl(blankToNull(req.actionUrl()))
                .payload(req.payload())
                .source(blankToNull(req.source()))
                .dedupeKey(dedupe)
                .build();
        applyStatusTimestamps(task, status, Instant.now());
        return toResponse(repo.save(task));
    }

    @Transactional
    public GuidedTaskResponse complete(UUID orgId, UUID taskId, UUID actorUserId, boolean serviceToken) {
        return setStatus(orgId, taskId, actorUserId, serviceToken, "completed");
    }

    @Transactional
    public GuidedTaskResponse dismiss(UUID orgId, UUID taskId, UUID actorUserId, boolean serviceToken) {
        return setStatus(orgId, taskId, actorUserId, serviceToken, "dismissed");
    }

    @Transactional
    public GuidedTaskResponse setStatus(
            UUID orgId, UUID taskId, UUID actorUserId, boolean serviceToken, String rawStatus) {
        GuidedTask task = repo.findByIdAndOrganizationId(taskId, orgId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));
        requireCanMutate(task, actorUserId, serviceToken);

        String status = normalizeStatus(rawStatus);
        task.setStatus(status);
        applyStatusTimestamps(task, status, Instant.now());
        return toResponse(repo.save(task));
    }

    private void requireCanMutate(GuidedTask task, UUID actorUserId, boolean serviceToken) {
        if (serviceToken) {
            return;
        }
        if (actorUserId == null || !actorUserId.equals(task.getSubjectUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed to update this task");
        }
    }

    private void applyStatusTimestamps(GuidedTask task, String status, Instant now) {
        if ("completed".equals(status)) {
            if (task.getCompletedAt() == null) task.setCompletedAt(now);
        }
        if ("dismissed".equals(status)) {
            if (task.getDismissedAt() == null) task.setDismissedAt(now);
        }
    }

    /** Free-form status: trim + lowercase; length capped. Apps may use any value. */
    static String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status is required");
        }
        String s = status.trim().toLowerCase(Locale.ROOT);
        if (s.length() > 64 || !s.matches("[a-z0-9_\\-./]+")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "status must be 1–64 chars: letters, digits, _ - . /");
        }
        return s;
    }

    static String normalizePriority(String priority) {
        if (priority == null || priority.isBlank()) return "normal";
        String p = priority.trim().toLowerCase(Locale.ROOT);
        return switch (p) {
            case "low", "normal", "high", "urgent" -> p;
            default -> "normal";
        };
    }

    private GuidedTaskResponse toResponse(GuidedTask t) {
        return new GuidedTaskResponse(
                t.getId(),
                t.getOrganizationId(),
                t.getTenantId(),
                t.getSubjectUserId(),
                t.getType(),
                t.getStatus(),
                t.getPriority(),
                t.getTitle(),
                t.getBody(),
                t.getActionUrl(),
                t.getPayload(),
                t.getSource(),
                t.getDedupeKey(),
                t.getCreatedAt(),
                t.getUpdatedAt(),
                t.getCompletedAt(),
                t.getDismissedAt()
        );
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
