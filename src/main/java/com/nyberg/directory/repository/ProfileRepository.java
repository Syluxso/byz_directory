package com.nyberg.directory.repository;

import com.nyberg.directory.domain.Profile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProfileRepository extends JpaRepository<Profile, UUID> {
    Optional<Profile> findByUserIdAndOrganizationId(UUID userId, UUID organizationId);
    Optional<Profile> findByOrganizationIdAndEmailIgnoreCase(UUID organizationId, String email);
    List<Profile> findByOrganizationId(UUID organizationId);
    boolean existsByOrganizationIdAndEmailIgnoreCase(UUID organizationId, String email);
    boolean existsByOrganizationIdAndOrgRole(UUID organizationId, String orgRole);
}
