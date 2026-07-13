package com.nyberg.directory.repository;

import com.nyberg.directory.domain.Membership;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MembershipRepository extends JpaRepository<Membership, UUID> {
    List<Membership> findByTenantId(UUID tenantId);
    List<Membership> findByUserIdAndOrganizationId(UUID userId, UUID organizationId);
    Optional<Membership> findByTenantIdAndUserId(UUID tenantId, UUID userId);
    boolean existsByTenantIdAndUserId(UUID tenantId, UUID userId);
    void deleteByTenantIdAndUserId(UUID tenantId, UUID userId);
}
