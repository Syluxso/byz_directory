package com.nyberg.directory.repository;

import com.nyberg.directory.domain.DirTenant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DirTenantRepository extends JpaRepository<DirTenant, UUID> {
    List<DirTenant> findByOrganizationIdAndDeletedAtIsNullOrderByNameAsc(UUID organizationId);
    Optional<DirTenant> findByIdAndOrganizationIdAndDeletedAtIsNull(UUID id, UUID organizationId);
}
