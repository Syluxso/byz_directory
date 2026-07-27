package com.nyberg.directory.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;

/**
 * Dual-writes Directory org/tenant roles into IAM so the next token refresh carries them.
 * Best-effort: failures are logged and do not roll back Directory writes.
 */
@Component
public class IamRoleClient {

    private static final Logger log = LoggerFactory.getLogger(IamRoleClient.class);

    private final RestClient http;
    private final boolean enabled;

    public IamRoleClient(
            @Value("${byz.iam.base-url:https://iam.byzantineapp.dev}") String iamBaseUrl,
            @Value("${byz.iam.role-sync-enabled:true}") boolean enabled
    ) {
        this.enabled = enabled;
        this.http = RestClient.builder().baseUrl(iamBaseUrl.replaceAll("/$", "")).build();
    }

    public void syncOrgRole(UUID organizationId, UUID userId, String orgRole) {
        if (!enabled) return;
        String token = bearerOrNull();
        if (token == null) {
            log.warn("IAM org-role sync skipped (no JWT) org={} user={}", organizationId, userId);
            return;
        }
        try {
            http.put()
                    .uri("/api/v1/admin/iam/orgs/{orgId}/users/{userId}/org-role", organizationId, userId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + token)
                    .body(Map.of("orgRole", orgRole == null ? "member" : orgRole))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("IAM org-role sync failed org={} user={}: {}", organizationId, userId, e.toString());
        }
    }

    public void syncTenantRole(UUID organizationId, UUID tenantId, UUID userId, String role) {
        if (!enabled) return;
        String token = bearerOrNull();
        if (token == null) {
            log.warn("IAM tenant-role sync skipped (no JWT) org={} tenant={} user={}", organizationId, tenantId, userId);
            return;
        }
        try {
            http.put()
                    .uri("/api/v1/admin/iam/orgs/{orgId}/users/{userId}/tenant-roles/{tenantId}",
                            organizationId, userId, tenantId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + token)
                    .body(Map.of("role", role == null ? "user" : role))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("IAM tenant-role sync failed org={} tenant={} user={}: {}",
                    organizationId, tenantId, userId, e.toString());
        }
    }

    public void revokeTenantRoles(UUID organizationId, UUID tenantId, UUID userId) {
        if (!enabled) return;
        String token = bearerOrNull();
        if (token == null) return;
        try {
            http.delete()
                    .uri("/api/v1/admin/iam/orgs/{orgId}/users/{userId}/tenant-roles/{tenantId}",
                            organizationId, userId, tenantId)
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("IAM tenant-role revoke failed org={} tenant={} user={}: {}",
                    organizationId, tenantId, userId, e.toString());
        }
    }

    private static String bearerOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getCredentials() instanceof Jwt jwt) {
            return jwt.getTokenValue();
        }
        return null;
    }
}
