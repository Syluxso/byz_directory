package com.nyberg.directory.service;

import com.nyberg.directory.client.IamRoleClient;
import com.nyberg.directory.domain.Profile;
import com.nyberg.directory.repository.ProfileRepository;
import com.nyberg.directory.security.AuthSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrgRoleService {

    private final ProfileRepository profiles;
    private final AuthSupport auth;
    private final IamRoleClient iamRoles;

    public Profile requireProfile(UUID userId, UUID organizationId) {
        return profiles.findByUserIdAndOrganizationId(userId, organizationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Directory profile required — call profiles/ensure first"));
    }

    public void requireOrgAdmin(UUID userId, UUID organizationId) {
        if (auth.isOrgAdminFromJwt()) {
            return;
        }
        // Migration fallback: Directory profile column until all tokens carry roles.
        Profile profile = requireProfile(userId, organizationId);
        if (!"admin".equalsIgnoreCase(profile.getOrgRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Organization admin required");
        }
    }

    public boolean isOrgAdmin(UUID userId, UUID organizationId) {
        if (auth.isOrgAdminFromJwt()) {
            return true;
        }
        return profiles.findByUserIdAndOrganizationId(userId, organizationId)
                .map(p -> "admin".equalsIgnoreCase(p.getOrgRole()))
                .orElse(false);
    }

    /** First profile in an org (or any ensure while no admin exists) becomes admin. */
    public String resolveRoleForNewProfile(UUID organizationId) {
        if (!profiles.existsByOrganizationIdAndOrgRole(organizationId, "admin")) {
            return "admin";
        }
        return "member";
    }

    public Profile promoteToAdminIfNoAdminExists(Profile profile) {
        if (!"admin".equalsIgnoreCase(profile.getOrgRole())
                && !profiles.existsByOrganizationIdAndOrgRole(profile.getOrganizationId(), "admin")) {
            profile.setOrgRole("admin");
            Profile saved = profiles.save(profile);
            iamRoles.syncOrgRole(saved.getOrganizationId(), saved.getUserId(), saved.getOrgRole());
            return saved;
        }
        return profile;
    }

    public void syncOrgRoleToIam(UUID organizationId, UUID userId, String orgRole) {
        iamRoles.syncOrgRole(organizationId, userId, orgRole);
    }
}
