package com.nyberg.directory.security;

import com.nyberg.directory.tenant.OrganizationContext;
import com.nyberg.directory.tenant.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Component
public class AuthSupport {

    public UUID requireUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UUID userId)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User JWT required");
        }
        return userId;
    }

    public UUID requireOrganizationId() {
        UUID orgId = OrganizationContext.get();
        if (orgId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "organization_id missing from token/context");
        }
        return orgId;
    }

    public UUID requireOrganizationId(UUID pathOrgId) {
        UUID tokenOrg = OrganizationContext.get();
        if (tokenOrg == null) {
            // Service tokens often omit organization_id; trust path org when service JWT.
            if (isServiceToken()) {
                OrganizationContext.set(pathOrgId);
                return pathOrgId;
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "organization_id missing from token/context");
        }
        if (!tokenOrg.equals(pathOrgId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Organization mismatch");
        }
        return tokenOrg;
    }

    public UUID requireTenantId() {
        // JwtToUuidConverter stores tenant_id on TenantContext; Spring often erases
        // UsernamePasswordAuthenticationToken credentials after auth, so prefer context.
        UUID fromCtx = TenantContext.get();
        if (fromCtx != null) {
            return fromCtx;
        }
        Jwt jwt = jwtOrNull();
        if (jwt == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "JWT required");
        }
        String raw = jwt.getClaimAsString("tenant_id");
        if (raw == null || raw.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tenant_id missing from token");
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid tenant_id in token");
        }
    }

    public boolean isServiceToken() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        if (auth.getPrincipal() instanceof UUID) return false;
        Object credentials = auth.getCredentials();
        if (credentials instanceof Jwt jwt) {
            String grant = jwt.getClaimAsString("grant_type");
            return "client_credentials".equals(grant) || "subject".equals(grant);
        }
        return true;
    }

    public Jwt jwtOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getCredentials() instanceof Jwt jwt) {
            return jwt;
        }
        return null;
    }

    /** Roles claim from IAM JWT (e.g. org:admin, tenant:user, tenant:admin:&lt;uuid&gt;). */
    public List<String> roles() {
        Jwt jwt = jwtOrNull();
        if (jwt == null) return List.of();
        Object raw = jwt.getClaim("roles");
        if (!(raw instanceof Collection<?> col)) return List.of();
        List<String> out = new ArrayList<>();
        for (Object item : col) {
            if (item != null) {
                String s = item.toString().trim().toLowerCase(Locale.ROOT);
                if (!s.isEmpty()) out.add(s);
            }
        }
        return out;
    }

    public boolean hasRole(String claim) {
        if (claim == null || claim.isBlank()) return false;
        String want = claim.trim().toLowerCase(Locale.ROOT);
        return roles().contains(want);
    }

    public boolean isOrgAdminFromJwt() {
        return hasRole("org:admin");
    }

    public boolean isTenantAdminFromJwt(UUID tenantId) {
        if (isOrgAdminFromJwt()) return true;
        if (hasRole("tenant:admin") && tenantId != null) {
            UUID tokenTenant = TenantContext.get();
            if (tokenTenant != null && tokenTenant.equals(tenantId)) {
                return true;
            }
        }
        return tenantId != null && hasRole("tenant:admin:" + tenantId);
    }
}