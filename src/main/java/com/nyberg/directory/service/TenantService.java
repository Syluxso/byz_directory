package com.nyberg.directory.service;

import com.nyberg.directory.client.IamRoleClient;
import com.nyberg.directory.domain.DirTenant;
import com.nyberg.directory.domain.Membership;
import com.nyberg.directory.dto.DirectoryDtos.*;
import com.nyberg.directory.messaging.TenantCreatedApplicationEvent;
import com.nyberg.directory.messaging.TenantLifecycleEvent;
import com.nyberg.directory.messaging.TenantMemberJoinedApplicationEvent;
import com.nyberg.directory.repository.DirTenantRepository;
import com.nyberg.directory.repository.MembershipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TenantService {

    private final DirTenantRepository tenants;
    private final MembershipRepository memberships;
    private final IamRoleClient iamRoles;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public List<TenantResponse> list(UUID organizationId) {
        return tenants.findByOrganizationIdAndDeletedAtIsNullOrderByNameAsc(organizationId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public TenantResponse get(UUID organizationId, UUID tenantId) {
        return toResponse(requireTenant(organizationId, tenantId));
    }

    @Transactional
    public TenantResponse create(UUID organizationId, UUID createdBy, CreateTenantRequest req) {
        String slug = req.slug() != null && !req.slug().isBlank()
                ? slugify(req.slug())
                : slugify(req.name());
        DirTenant tenant = tenants.save(DirTenant.builder()
                .organizationId(organizationId)
                .name(req.name().trim())
                .slug(slug.isBlank() ? null : slug)
                .description(blankToNull(req.description()))
                .createdBy(createdBy)
                .build());
        // Creator becomes tenant admin so they can invite / manage the team immediately.
        if (createdBy != null && !memberships.existsByTenantIdAndUserId(tenant.getId(), createdBy)) {
            memberships.save(Membership.builder()
                    .tenantId(tenant.getId())
                    .userId(createdBy)
                    .organizationId(organizationId)
                    .role("admin")
                    .build());
            iamRoles.syncTenantRole(organizationId, tenant.getId(), createdBy, "admin");
        }
        events.publishEvent(new TenantCreatedApplicationEvent(
                this,
                TenantLifecycleEvent.tenantCreated(
                        organizationId,
                        tenant.getId(),
                        createdBy,
                        tenant.getName(),
                        tenant.getSlug()
                )
        ));
        if (createdBy != null) {
            events.publishEvent(new TenantMemberJoinedApplicationEvent(
                    this,
                    TenantLifecycleEvent.memberJoined(
                            organizationId,
                            tenant.getId(),
                            createdBy,
                            tenant.getName(),
                            tenant.getSlug()
                    )
            ));
        }
        return toResponse(tenant);
    }

    /**
     * Idempotent bootstrap for signup: create tenant with the given IAM tenant id (if absent)
     * and ensure the caller is a tenant admin. JWT tenant_id must match {@code req.id()}.
     */
    @Transactional
    public TenantResponse ensure(UUID organizationId, UUID callerUserId, EnsureTenantRequest req) {
        Optional<DirTenant> existing = tenants.findByIdAndOrganizationIdAndDeletedAtIsNull(req.id(), organizationId);
        DirTenant tenant;
        if (existing.isPresent()) {
            tenant = existing.get();
        } else {
            String slug = req.slug() != null && !req.slug().isBlank()
                    ? slugify(req.slug())
                    : slugify(req.name());
            tenant = tenants.save(DirTenant.builder()
                    .id(req.id())
                    .organizationId(organizationId)
                    .name(req.name().trim())
                    .slug(slug.isBlank() ? null : slug)
                    .description(blankToNull(req.description()))
                    .createdBy(callerUserId)
                    .build());
        }
        boolean newlyJoined = false;
        if (callerUserId != null && !memberships.existsByTenantIdAndUserId(tenant.getId(), callerUserId)) {
            boolean firstMember = memberships.findByTenantId(tenant.getId()).isEmpty();
            String role = firstMember ? "admin" : "user";
            memberships.save(Membership.builder()
                    .tenantId(tenant.getId())
                    .userId(callerUserId)
                    .organizationId(organizationId)
                    .role(role)
                    .build());
            iamRoles.syncTenantRole(organizationId, tenant.getId(), callerUserId, role);
            newlyJoined = true;
        }
        if (existing.isEmpty()) {
            events.publishEvent(new TenantCreatedApplicationEvent(
                    this,
                    TenantLifecycleEvent.tenantCreated(
                            organizationId,
                            tenant.getId(),
                            callerUserId,
                            tenant.getName(),
                            tenant.getSlug()
                    )
            ));
        }
        if (newlyJoined && callerUserId != null) {
            events.publishEvent(new TenantMemberJoinedApplicationEvent(
                    this,
                    TenantLifecycleEvent.memberJoined(
                            organizationId,
                            tenant.getId(),
                            callerUserId,
                            tenant.getName(),
                            tenant.getSlug()
                    )
            ));
        }
        return toResponse(tenant);
    }

    @Transactional
    public TenantResponse update(UUID organizationId, UUID tenantId, UpdateTenantRequest req) {
        DirTenant tenant = requireTenant(organizationId, tenantId);
        if (req.name() != null && !req.name().isBlank()) {
            tenant.setName(req.name().trim());
        }
        if (req.slug() != null) {
            String slug = req.slug().isBlank() ? null : slugify(req.slug());
            tenant.setSlug(slug == null || slug.isBlank() ? null : slug);
        }
        if (req.description() != null) {
            tenant.setDescription(blankToNull(req.description()));
        }
        return toResponse(tenants.save(tenant));
    }

    @Transactional
    public void softDelete(UUID organizationId, UUID tenantId) {
        DirTenant tenant = requireTenant(organizationId, tenantId);
        tenant.setDeletedAt(Instant.now());
        tenants.save(tenant);
    }

    public DirTenant requireTenant(UUID organizationId, UUID tenantId) {
        return tenants.findByIdAndOrganizationIdAndDeletedAtIsNull(tenantId, organizationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));
    }

    private TenantResponse toResponse(DirTenant t) {
        return new TenantResponse(
                t.getId(), t.getOrganizationId(), t.getName(), t.getSlug(), t.getDescription(),
                t.getCreatedBy(), t.getCreatedAt(), t.getUpdatedAt());
    }

    private static String slugify(String input) {
        return input.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
