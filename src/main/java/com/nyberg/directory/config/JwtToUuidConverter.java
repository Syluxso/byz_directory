package com.nyberg.directory.config;

import com.nyberg.directory.tenant.OrganizationContext;
import com.nyberg.directory.tenant.TenantContext;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class JwtToUuidConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        UUID userId = null;
        try {
            userId = UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException ignored) {
            // Service / client-credentials tokens use non-UUID subjects.
        }

        String orgId = jwt.getClaimAsString("organization_id");
        if (orgId != null) {
            try {
                OrganizationContext.set(UUID.fromString(orgId));
            } catch (IllegalArgumentException ignored) {}
        }

        String tenantId = jwt.getClaimAsString("tenant_id");
        if (tenantId != null) {
            try {
                TenantContext.set(UUID.fromString(tenantId));
            } catch (IllegalArgumentException ignored) {}
        }

        // Keep Jwt as credentials; disable erase so AuthSupport.jwtOrNull() still works.
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(userId, jwt, List.of()) {
                    @Override
                    public void eraseCredentials() {
                        // no-op: retain Jwt for claim access (tenant_id, grant_type)
                    }
                };
        return auth;
    }
}