package com.nyberg.directory.service;

import com.nyberg.directory.domain.DirTenant;
import com.nyberg.directory.domain.Membership;
import com.nyberg.directory.domain.OrganizationProfile;
import com.nyberg.directory.domain.Profile;
import com.nyberg.directory.dto.ToolsDirectoryDtos.*;
import com.nyberg.directory.repository.AddressCardRepository;
import com.nyberg.directory.repository.ContactCardRepository;
import com.nyberg.directory.repository.DirTenantRepository;
import com.nyberg.directory.repository.MembershipRepository;
import com.nyberg.directory.repository.OrganizationProfileRepository;
import com.nyberg.directory.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ToolsDirectoryService {

    private final ProfileRepository profiles;
    private final OrganizationProfileRepository orgProfiles;
    private final MembershipRepository memberships;
    private final DirTenantRepository tenants;
    private final ContactCardRepository contactCards;
    private final AddressCardRepository addressCards;

    /**
     * Public directory view for the given user in the given org.
     * No internal IDs exposed. Missing person profile is not an error — org and
     * tenant memberships still resolve from session ids.
     */
    @Transactional(readOnly = true)
    public ToolsWhoamiResponse whoami(UUID userId, UUID organizationId) {
        if (userId == null || organizationId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "user and organization required");
        }

        Profile profile = profiles.findByUserIdAndOrganizationId(userId, organizationId).orElse(null);
        boolean profileFound = profile != null;

        PublicPerson person = null;
        if (profile != null) {
            person = new PublicPerson(
                    blankToNull(profile.getDisplayName()),
                    blankToNull(profile.getEmail()),
                    profile.getOrgRole() != null ? profile.getOrgRole() : "member",
                    toPublicContact(profile.getContactCardId()),
                    toPublicAddress(profile.getAddressCardId())
            );
        }

        OrganizationProfile org = orgProfiles.findById(organizationId)
                .orElseGet(() -> OrganizationProfile.builder().organizationId(organizationId).build());

        PublicOrganization organization = new PublicOrganization(
                blankToNull(org.getDisplayName()),
                toPublicContact(org.getContactCardId()),
                toPublicAddress(org.getAddressCardId())
        );

        List<PublicTenantMembership> tenantList = new ArrayList<>();
        for (Membership m : memberships.findByUserIdAndOrganizationId(userId, organizationId)) {
            DirTenant t = tenants.findByIdAndOrganizationIdAndDeletedAtIsNull(m.getTenantId(), organizationId)
                    .orElse(null);
            if (t == null) {
                continue;
            }
            tenantList.add(new PublicTenantMembership(
                    blankToNull(t.getName()),
                    blankToNull(t.getSlug()),
                    blankToNull(t.getDescription()),
                    m.getRole() != null ? m.getRole() : "member"
            ));
        }

        return new ToolsWhoamiResponse(profileFound, person, organization, tenantList);
    }

    private PublicContact toPublicContact(UUID cardId) {
        if (cardId == null) {
            return null;
        }
        return contactCards.findById(cardId)
                .map(c -> new PublicContact(
                        blankToNull(c.getEmail()),
                        blankToNull(c.getPhone()),
                        blankToNull(c.getWebsite()),
                        blankToNull(c.getLabel())
                ))
                .orElse(null);
    }

    private PublicAddress toPublicAddress(UUID cardId) {
        if (cardId == null) {
            return null;
        }
        return addressCards.findById(cardId)
                .map(a -> new PublicAddress(
                        blankToNull(a.getLine1()),
                        blankToNull(a.getLine2()),
                        blankToNull(a.getCity()),
                        blankToNull(a.getRegion()),
                        blankToNull(a.getPostalCode()),
                        blankToNull(a.getCountry()),
                        blankToNull(a.getLabel())
                ))
                .orElse(null);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
