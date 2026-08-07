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
    private final OrgRoleService orgRoles;

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
                .map(existing -> {
                    Profile p = orgRoles.promoteToAdminIfNoAdminExists(existing);
                    orgRoles.syncOrgRoleToIam(organizationId, userId, p.getOrgRole());
                    return toProfileResponse(p);
                })
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
                            .orgRole(orgRoles.resolveRoleForNewProfile(organizationId))
                            .metadata(req.metadata())
                            .build());
                    orgRoles.syncOrgRoleToIam(organizationId, userId, created.getOrgRole());
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

    /**
     * Idempotent profile hydration from IAM lifecycle events ({@code user.registered},
     * {@code user.authenticated}). Creates a profile if missing; fills empty displayName /
     * email, or upgrades an email-local-part placeholder (e.g. {@code syluxso} from
     * {@code syluxso@gmail.com}) when the IdP sends a real name. Never overwrites a
     * non-placeholder display name. Phone is not set here.
     */
    @Transactional
    public void applyIdentityHint(UUID userId, UUID organizationId, String email, String displayName) {
        if (userId == null || organizationId == null) {
            return;
        }
        String mail = email == null || email.isBlank() ? null : normalizeEmail(email);
        String display = blankToNull(displayName);

        profiles.findByUserIdAndOrganizationId(userId, organizationId).ifPresentOrElse(existing -> {
            boolean changed = false;
            if (display != null && shouldReplaceDisplayName(existing.getDisplayName(), existing.getEmail(), mail)) {
                existing.setDisplayName(display);
                changed = true;
            }
            if ((existing.getEmail() == null || existing.getEmail().isBlank()) && mail != null) {
                if (!profiles.existsByOrganizationIdAndEmailIgnoreCase(organizationId, mail)
                        || mail.equalsIgnoreCase(existing.getEmail())) {
                    existing.setEmail(mail);
                    changed = true;
                }
            }
            if (changed) {
                profiles.save(existing);
            }
        }, () -> {
            if (mail == null) {
                return;
            }
            if (profiles.existsByOrganizationIdAndEmailIgnoreCase(organizationId, mail)) {
                // Another userId already owns this email in the org — skip create.
                return;
            }
            String name = display != null
                    ? display
                    : (mail.contains("@") ? mail.substring(0, mail.indexOf('@')) : mail);
            Profile created = profiles.save(Profile.builder()
                    .userId(userId)
                    .organizationId(organizationId)
                    .email(mail)
                    .displayName(name)
                    .orgRole(orgRoles.resolveRoleForNewProfile(organizationId))
                    .build());
            orgRoles.syncOrgRoleToIam(organizationId, userId, created.getOrgRole());
        });
    }

    /**
     * Replace when blank, or when current name is just the email local-part default
     * produced by ensure/register (not a user-chosen full name).
     */
    static boolean shouldReplaceDisplayName(String currentDisplay, String profileEmail, String hintEmail) {
        String current = blankToNull(currentDisplay);
        if (current == null) {
            return true;
        }
        String mail = profileEmail != null && !profileEmail.isBlank()
                ? normalizeEmail(profileEmail)
                : (hintEmail != null ? normalizeEmail(hintEmail) : null);
        if (mail == null || !mail.contains("@")) {
            return false;
        }
        String local = mail.substring(0, mail.indexOf('@'));
        return current.equalsIgnoreCase(local);
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
                p.getOrgRole() != null ? p.getOrgRole() : "member",
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
