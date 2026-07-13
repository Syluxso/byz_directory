package com.nyberg.directory.security;

import com.nyberg.directory.tenant.OrganizationContext;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

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
        UUID tokenOrg = requireOrganizationId();
        if (!tokenOrg.equals(pathOrgId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Organization mismatch");
        }
        return tokenOrg;
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
}
