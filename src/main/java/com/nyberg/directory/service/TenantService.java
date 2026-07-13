package com.nyberg.directory.service;

import com.nyberg.directory.domain.DirTenant;
import com.nyberg.directory.domain.Membership;
import com.nyberg.directory.dto.DirectoryDtos.*;
import com.nyberg.directory.repository.DirTenantRepository;
import com.nyberg.directory.repository.MembershipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TenantService {

    private final DirTenantRepository tenants;
    private final MembershipRepository memberships;

    @Transactional(readOnly = true)
    public List<TenantResponse> list(UUID organizationId) {
        return tenants.findByOrganizationIdOrderByNameAsc(organizationId).stream().map(this::toResponse).toList();
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
                .slug(slug)
                .description(blankToNull(req.description()))
                .createdBy(createdBy)
                .build());

        // Creator becomes tenant admin.
        memberships.save(Membership.builder()
                .tenantId(tenant.getId())
                .userId(createdBy)
                .organizationId(organizationId)
                .role("admin")
                .build());

        return toResponse(tenant);
    }

    @Transactional
    public TenantResponse update(UUID organizationId, UUID tenantId, UpdateTenantRequest req) {
        DirTenant tenant = requireTenant(organizationId, tenantId);
        if (req.name() != null && !req.name().isBlank()) {
            tenant.setName(req.name().trim());
        }
        if (req.slug() != null) {
            tenant.setSlug(req.slug().isBlank() ? null : slugify(req.slug()));
        }
        if (req.description() != null) {
            tenant.setDescription(blankToNull(req.description()));
        }
        return toResponse(tenants.save(tenant));
    }

    @Transactional
    public void delete(UUID organizationId, UUID tenantId) {
        DirTenant tenant = requireTenant(organizationId, tenantId);
        tenants.delete(tenant);
    }

    public DirTenant requireTenant(UUID organizationId, UUID tenantId) {
        return tenants.findByIdAndOrganizationId(tenantId, organizationId)
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
