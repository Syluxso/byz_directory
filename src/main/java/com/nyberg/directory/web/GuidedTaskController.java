package com.nyberg.directory.web;

import com.nyberg.directory.dto.DirectoryDtos.CreateGuidedTaskRequest;
import com.nyberg.directory.dto.DirectoryDtos.GuidedTaskResponse;
import com.nyberg.directory.dto.DirectoryDtos.UpdateGuidedTaskStatusRequest;
import com.nyberg.directory.security.AuthSupport;
import com.nyberg.directory.service.GuidedTaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * Guided / system tasks for business apps (Hamlet, etc.).
 * Status is free-form (conventional: open, completed, dismissed, cancelled).
 */
@RestController
@RequestMapping("/api/v1/orgs/{orgId}/tasks")
@RequiredArgsConstructor
public class GuidedTaskController {

    private final AuthSupport auth;
    private final GuidedTaskService tasks;

    /** Current user's tasks in this org. */
    @GetMapping("/me")
    public List<GuidedTaskResponse> listMine(
            @PathVariable UUID orgId,
            @RequestParam(required = false) String status) {
        auth.requireOrganizationId(orgId);
        UUID userId = auth.requireUserId();
        return tasks.listForSubject(orgId, userId, status);
    }

    /**
     * List tasks for a subject. User JWT: only self. Service token: any subject in org.
     */
    @GetMapping
    public List<GuidedTaskResponse> list(
            @PathVariable UUID orgId,
            @RequestParam UUID subjectUserId,
            @RequestParam(required = false) String status) {
        auth.requireOrganizationId(orgId);
        if (auth.isServiceToken()) {
            return tasks.listForSubject(orgId, subjectUserId, status);
        }
        UUID userId = auth.requireUserId();
        if (!userId.equals(subjectUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Can only list your own tasks");
        }
        return tasks.listForSubject(orgId, subjectUserId, status);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GuidedTaskResponse create(
            @PathVariable UUID orgId,
            @Valid @RequestBody CreateGuidedTaskRequest req) {
        auth.requireOrganizationId(orgId);
        UUID subject = resolveSubjectForCreate(req);
        return tasks.create(orgId, subject, req);
    }

    @PostMapping("/{taskId}/complete")
    public GuidedTaskResponse complete(@PathVariable UUID orgId, @PathVariable UUID taskId) {
        auth.requireOrganizationId(orgId);
        return tasks.complete(orgId, taskId, actorOrNull(), auth.isServiceToken());
    }

    @PostMapping("/{taskId}/dismiss")
    public GuidedTaskResponse dismiss(@PathVariable UUID orgId, @PathVariable UUID taskId) {
        auth.requireOrganizationId(orgId);
        return tasks.dismiss(orgId, taskId, actorOrNull(), auth.isServiceToken());
    }

    /** Set any status string (apps may invent values beyond open/completed/dismissed). */
    @PatchMapping("/{taskId}/status")
    public GuidedTaskResponse updateStatus(
            @PathVariable UUID orgId,
            @PathVariable UUID taskId,
            @Valid @RequestBody UpdateGuidedTaskStatusRequest req) {
        auth.requireOrganizationId(orgId);
        return tasks.setStatus(orgId, taskId, actorOrNull(), auth.isServiceToken(), req.status());
    }

    private UUID resolveSubjectForCreate(CreateGuidedTaskRequest req) {
        if (auth.isServiceToken()) {
            if (req.subjectUserId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "subjectUserId is required for service tokens");
            }
            return req.subjectUserId();
        }
        UUID userId = auth.requireUserId();
        if (req.subjectUserId() != null && !req.subjectUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Users can only create tasks for themselves");
        }
        return userId;
    }

    private UUID actorOrNull() {
        if (auth.isServiceToken()) {
            return null;
        }
        return auth.requireUserId();
    }
}
