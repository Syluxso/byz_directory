package com.nyberg.directory.web;

import com.nyberg.directory.dto.ToolsDirectoryDtos.ToolsWhoamiResponse;
import com.nyberg.directory.security.AuthSupport;
import com.nyberg.directory.service.ToolsDirectoryService;
import com.nyberg.directory.tenant.OrganizationContext;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Agent-only tools surface. Not for product UIs or third-party integrations.
 * Returns public directory facts for the chat caller (no internal UUIDs).
 *
 * <p>Auth:
 * <ul>
 *   <li>User JWT: uses token subject + organization_id (query overrides ignored unless platform admin).
 *   <li>Service JWT (client_credentials): must be platform admin org; requires userId + organizationId query params
 *       (same pattern as byz-search for byz-agent).
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/tools/directory")
@RequiredArgsConstructor
public class ToolsDirectoryController {

    private final ToolsDirectoryService toolsDirectory;
    private final AuthSupport auth;

    @Value("${byz.tools.admin-organization-id:}")
    private String adminOrganizationId;

    /**
     * Public profile of the current chat user + their org + tenant memberships (names only).
     */
    @GetMapping("/me")
    public ToolsWhoamiResponse me(
            @RequestParam(required = false) UUID organizationId,
            @RequestParam(required = false) UUID userId) {
        ResolvedSubject subject = resolveSubject(organizationId, userId);
        return toolsDirectory.whoami(subject.userId(), subject.organizationId());
    }

    private record ResolvedSubject(UUID userId, UUID organizationId) {}

    private ResolvedSubject resolveSubject(UUID requestOrgId, UUID requestUserId) {
        boolean service = auth.isServiceToken();
        boolean platformAdmin = isPlatformAdmin();

        if (service) {
            if (!platformAdmin) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "service token requires platform admin organization");
            }
            if (requestOrgId == null || requestUserId == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "service callers must pass organizationId and userId");
            }
            return new ResolvedSubject(requestUserId, requestOrgId);
        }

        // User JWT
        UUID tokenUser = auth.requireUserId();
        UUID tokenOrg = OrganizationContext.get();
        if (tokenOrg == null) {
            Jwt jwt = auth.jwtOrNull();
            if (jwt != null) {
                String raw = jwt.getClaimAsString("organization_id");
                if (raw != null && !raw.isBlank()) {
                    try {
                        tokenOrg = UUID.fromString(raw);
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
        }
        if (tokenOrg == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "organization_id missing from token");
        }

        UUID orgId = tokenOrg;
        UUID user = tokenUser;

        if (requestOrgId != null && !requestOrgId.equals(tokenOrg)) {
            if (!platformAdmin) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "organizationId override not allowed");
            }
            orgId = requestOrgId;
        }
        if (requestUserId != null && !requestUserId.equals(tokenUser)) {
            if (!platformAdmin) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "userId override not allowed");
            }
            user = requestUserId;
        }

        // Silence unused auth field warning paths
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "not authenticated");
        }

        return new ResolvedSubject(user, orgId);
    }

    private boolean isPlatformAdmin() {
        String admin = adminOrganizationId == null ? "" : adminOrganizationId.trim();
        if (admin.isEmpty()) {
            // Local/dev: allow overrides when unset (same spirit as byz-search).
            return true;
        }
        UUID tokenOrg = OrganizationContext.get();
        if (tokenOrg == null) {
            Jwt jwt = auth.jwtOrNull();
            if (jwt != null) {
                String raw = jwt.getClaimAsString("organization_id");
                if (raw != null) {
                    try {
                        tokenOrg = UUID.fromString(raw);
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
        }
        try {
            return tokenOrg != null && tokenOrg.equals(UUID.fromString(admin));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
