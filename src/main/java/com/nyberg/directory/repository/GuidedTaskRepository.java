package com.nyberg.directory.repository;

import com.nyberg.directory.domain.GuidedTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GuidedTaskRepository extends JpaRepository<GuidedTask, UUID> {

    List<GuidedTask> findByOrganizationIdAndSubjectUserIdOrderByCreatedAtDesc(
            UUID organizationId, UUID subjectUserId);

    List<GuidedTask> findByOrganizationIdAndSubjectUserIdAndStatusIgnoreCaseOrderByCreatedAtDesc(
            UUID organizationId, UUID subjectUserId, String status);

    Optional<GuidedTask> findByIdAndOrganizationId(UUID id, UUID organizationId);

    @Query("""
            select t from GuidedTask t
            where t.organizationId = :orgId
              and t.subjectUserId = :subjectUserId
              and t.type = :type
              and t.status = 'open'
              and (
                   (:dedupeKey is null and t.dedupeKey is null)
                or t.dedupeKey = :dedupeKey
              )
            """)
    Optional<GuidedTask> findOpenDedupe(
            @Param("orgId") UUID orgId,
            @Param("subjectUserId") UUID subjectUserId,
            @Param("type") String type,
            @Param("dedupeKey") String dedupeKey);
}
