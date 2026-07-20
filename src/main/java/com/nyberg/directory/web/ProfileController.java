package com.nyberg.directory.web;

import com.nyberg.directory.dto.DirectoryDtos.*;
import com.nyberg.directory.security.AuthSupport;
import com.nyberg.directory.service.MembershipService;
import com.nyberg.directory.service.OrgRoleService;
import com.nyberg.directory.service.ProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profiles;
    private final MembershipService memberships;
    private final OrgRoleService orgRoles;
    private final AuthSupport auth;

    @GetMapping("/orgs/{orgId}/profile")
    public OrgProfileResponse getOrgProfile(@PathVariable UUID orgId) {
        auth.requireOrganizationId(orgId);
        return profiles.getOrgProfile(orgId);
    }

    @PutMapping("/orgs/{orgId}/profile")
    public OrgProfileResponse upsertOrgProfile(@PathVariable UUID orgId, @RequestBody OrgProfileRequest req) {
        auth.requireOrganizationId(orgId);
        orgRoles.requireOrgAdmin(auth.requireUserId(), orgId);
        return profiles.upsertOrgProfile(orgId, req);
    }

    @PostMapping("/orgs/{orgId}/profiles/ensure")
    public ProfileResponse ensureProfile(@PathVariable UUID orgId, @Valid @RequestBody EnsureProfileRequest req) {
        auth.requireOrganizationId(orgId);
        UUID userId = auth.requireUserId();
        return profiles.ensureProfile(userId, orgId, req);
    }

    @GetMapping("/orgs/{orgId}/profiles/me")
    public ProfileResponse myProfile(@PathVariable UUID orgId) {
        auth.requireOrganizationId(orgId);
        return profiles.getMyProfile(auth.requireUserId(), orgId);
    }

    @GetMapping("/orgs/{orgId}/profiles")
    public List<ProfileResponse> listProfiles(@PathVariable UUID orgId) {
        auth.requireOrganizationId(orgId);
        auth.requireUserId();
        return profiles.listProfiles(orgId);
    }

    @GetMapping("/orgs/{orgId}/profiles/{userId}")
    public ProfileResponse getProfile(@PathVariable UUID orgId, @PathVariable UUID userId) {
        auth.requireOrganizationId(orgId);
        auth.requireUserId();
        return profiles.getProfile(orgId, userId);
    }

    @PutMapping("/orgs/{orgId}/profiles/{userId}")
    public ProfileResponse updateProfile(
            @PathVariable UUID orgId,
            @PathVariable UUID userId,
            @RequestBody UpdateProfileRequest req) {
        auth.requireOrganizationId(orgId);
        UUID actor = auth.requireUserId();
        return profiles.updateProfile(actor, orgId, userId, req);
    }

    @GetMapping("/orgs/{orgId}/memberships/me")
    public List<MembershipResponse> myMemberships(@PathVariable UUID orgId) {
        auth.requireOrganizationId(orgId);
        return memberships.listMyMemberships(auth.requireUserId(), orgId);
    }
}
