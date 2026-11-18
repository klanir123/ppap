package com.msg.ppap.masterdata.adapter.out.jpa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SubmissionLevelJpaRepository extends JpaRepository<SubmissionLevelEntity, UUID> {

    Optional<SubmissionLevelEntity> findByTenantIdAndId(String tenantId, UUID id);

    Optional<SubmissionLevelEntity> findByTenantIdAndCode(String tenantId, String code);

    @Query(
            "select l from SubmissionLevelEntity l where l.tenantId = :tenant and (:active is null or l.active = :active)")
    List<SubmissionLevelEntity> list(@Param("tenant") String tenant, @Param("active") Boolean active, Sort sort);
}
