package com.nyberg.directory.repository;

import com.nyberg.directory.domain.Invite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InviteRepository extends JpaRepository<Invite, UUID> {
    Optional<Invite> findByToken(String token);

    List<Invite> findByTenantIdAndStatus(UUID tenantId, String status);

    @Query("""
        select i from Invite i
        where i.organizationId = :orgId
          and lower(i.email) = lower(:email)
          and i.status = 'pending'
        """)
    List<Invite> findPendingByOrgAndEmail(@Param("orgId") UUID orgId, @Param("email") String email);

    List<Invite> findByOrganizationIdAndStatus(UUID organizationId, String status);
}
