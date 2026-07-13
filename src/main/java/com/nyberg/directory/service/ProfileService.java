package com.nyberg.directory.service;

import com.nyberg.directory.domain.AddressCard;
import com.nyberg.directory.domain.ContactCard;
import com.nyberg.directory.domain.OrganizationProfile;
import com.nyberg.directory.domain.Profile;
import com.nyberg.directory.dto.DirectoryDtos.*;
import com.nyberg.directory.repository.OrganizationProfileRepository;
import com.nyberg.directory.repository.ProfileRepository;
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
public class ProfileService {

    private final ProfileRepository profiles;
    private final OrganizationProfileRepository orgProfiles;
    private final CardService cards;

    @Transactional(readOnly = true)
    public OrgProfileResponse getOrgProfile(UUID organizationId) {
        OrganizationProfile profile = orgProfiles.findById(organizationId)
                .orElseGet(() -> OrganizationProfile.builder()
                        .organizationId(organizationId)
                        .build());
        return toOrgResponse(profile);
    }

    @Transactional
    public OrgProfileResponse upsertOrgProfile(UUID organizationId, OrgProfileRequest req) {
        OrganizationProfile profile = orgProfiles.findById(organizationId)
                .orElseGet(() -> OrganizationProfile.builder().organizationId(organizationId).build());

        if (req.displayName() != null) {
            profile.setDisplayName(blankToNull(req.displayName()));
        }
        if (req.metadata() != null) {
            profile.setMetadata(req.metadata());
        }
        if (req.contact() != null) {
            ContactCard contact = cards.createOrUpdateContact(organizationId, profile.getContactCardId(), req.contact());
            profile.setContactCardId(contact.getId());
        }
        if (req.address() != null) {
            AddressCard address = cards.createOrUpdateAddress(organizationId, profile.getAddressCardId(), req.address());
            profile.setAddressCardId(address.getId());
        }
        return toOrgResponse(orgProfiles.save(profile));
    }

    @Transactional
    public ProfileResponse ensureProfile(UUID userId, UUID organizationId, EnsureProfileRequest req) {
        return profiles.findByUserIdAndOrganizationId(userId, organizationId)
                .map(this::toProfileResponse)
                .orElseGet(() -> {
                    String email = normalizeEmail(req.email());
                    if (profiles.existsByOrganizationIdAndEmailIgnoreCase(organizationId, email)) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT,
                                "Email already bound to another user in this organization");
                    }
                    String display = blankToNull(req.displayName());
                    if (display == null) {
                        display = email.contains("@") ? email.substring(0, email.indexOf('@')) : email;
                    }
                    Profile created = profiles.save(Profile.builder()
                            .userId(userId)
                            .organizationId(organizationId)
                            .email(email)
                            .displayName(display)
                            .metadata(req.metadata())
                            .build());
                    return toProfileResponse(created);
                });
    }

    @Transactional(readOnly = true)
    public ProfileResponse getMyProfile(UUID userId, UUID organizationId) {
        return profiles.findByUserIdAndOrganizationId(userId, organizationId)
                .map(this::toProfileResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found"));
    }

    @Transactional(readOnly = true)
    public ProfileResponse getProfile(UUID organizationId, UUID userId) {
        return profiles.findByUserIdAndOrganizationId(userId, organizationId)
                .map(this::toProfileResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found"));
    }

    @Transactional(readOnly = true)
    public List<ProfileResponse> listProfiles(UUID organizationId) {
        return profiles.findByOrganizationId(organizationId).stream().map(this::toProfileResponse).toList();
    }

    @Transactional
    public ProfileResponse updateProfile(UUID actorUserId, UUID organizationId, UUID targetUserId, UpdateProfileRequest req) {
        if (!actorUserId.equals(targetUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Can only update your own profile");
        }
        Profile profile = profiles.findByUserIdAndOrganizationId(targetUserId, organizationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found"));

        if (req.email() != null && !req.email().isBlank()) {
            String email = normalizeEmail(req.email());
            if (!email.equalsIgnoreCase(profile.getEmail())
                    && profiles.existsByOrganizationIdAndEmailIgnoreCase(organizationId, email)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already in use in this organization");
            }
            profile.setEmail(email);
        }
        if (req.displayName() != null) {
            profile.setDisplayName(blankToNull(req.displayName()));
        }
        if (req.metadata() != null) {
            profile.setMetadata(req.metadata());
        }
        if (req.contact() != null) {
            ContactCard contact = cards.createOrUpdateContact(organizationId, profile.getContactCardId(), req.contact());
            profile.setContactCardId(contact.getId());
        }
        if (req.address() != null) {
            AddressCard address = cards.createOrUpdateAddress(organizationId, profile.getAddressCardId(), req.address());
            profile.setAddressCardId(address.getId());
        }
        return toProfileResponse(profiles.save(profile));
    }

    private OrgProfileResponse toOrgResponse(OrganizationProfile p) {
        return new OrgProfileResponse(
                p.getOrganizationId(),
                p.getDisplayName(),
                cards.toContactResponse(p.getContactCardId()),
                cards.toAddressResponse(p.getAddressCardId()),
                p.getMetadata(),
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }

    private ProfileResponse toProfileResponse(Profile p) {
        return new ProfileResponse(
                p.getId(),
                p.getUserId(),
                p.getOrganizationId(),
                p.getEmail(),
                p.getDisplayName(),
                cards.toContactResponse(p.getContactCardId()),
                cards.toAddressResponse(p.getAddressCardId()),
                p.getMetadata(),
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
