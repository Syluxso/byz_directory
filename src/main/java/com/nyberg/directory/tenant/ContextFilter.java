package com.nyberg.directory.tenant;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(1)
public class ContextFilter implements Filter {

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        if (request.getRequestURI().startsWith("/actuator")) {
            chain.doFilter(req, res);
            return;
        }

        String header = request.getHeader("X-Tenant-ID");
        if (header != null && !header.isBlank()) {
            try {
                TenantContext.set(UUID.fromString(header));
            } catch (IllegalArgumentException e) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "X-Tenant-ID must be a valid UUID");
                return;
            }
        }

        String orgHeader = request.getHeader("X-Organization-ID");
        if (orgHeader != null && !orgHeader.isBlank()) {
            try {
                OrganizationContext.set(UUID.fromString(orgHeader));
            } catch (IllegalArgumentException e) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "X-Organization-ID must be a valid UUID");
                return;
            }
        }

        try {
            chain.doFilter(req, res);
        } finally {
            TenantContext.clear();
            OrganizationContext.clear();
        }
    }
}
