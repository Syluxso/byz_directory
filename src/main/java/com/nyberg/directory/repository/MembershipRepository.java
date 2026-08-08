package com.nyberg.directory.repository;

import com.nyberg.directory.domain.Membership;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MembershipRepository extends JpaRepository<Membership, UUID> {

    List<Membership> findByTenantId(UUID tenantId);

    List<Membership> findByTenantIdAndStatusIn(UUID tenantId, List<String> statuses);

    List<Membership> findByUserIdAndOrganizationIdAndStatus(UUID userId, UUID organizationId, String status);

    Optional<Membership> findByTenantIdAndUserId(UUID tenantId, UUID userId);

    boolean existsByTenantIdAndUserId(UUID tenantId, UUID userId);

    boolean existsByTenantIdAndUserIdAndStatus(UUID tenantId, UUID userId, String status);

    long countByTenantIdAndRoleAndStatus(UUID tenantId, String role, String status);

    @Query("""
        select count(m) from Membership m
        where m.tenantId = :tenantId
          and m.role = 'admin'
          and m.status = 'active'
        """)
    long countActiveAdmins(@Param("tenantId") UUID tenantId);

    void deleteByTenantIdAndUserId(UUID tenantId, UUID userId);
}
