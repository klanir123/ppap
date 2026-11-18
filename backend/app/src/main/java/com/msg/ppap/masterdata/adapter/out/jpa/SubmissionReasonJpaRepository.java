package com.msg.ppap.masterdata.adapter.out.jpa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SubmissionReasonJpaRepository extends JpaRepository<SubmissionReasonEntity, UUID> {

    Optional<SubmissionReasonEntity> findByTenantIdAndId(String tenantId, UUID id);

    Optional<SubmissionReasonEntity> findByTenantIdAndCode(String tenantId, String code);

    @Query(
            "select r from SubmissionReasonEntity r where r.tenantId = :tenant and (:active is null or r.active = :active)")
    List<SubmissionReasonEntity> list(@Param("tenant") String tenant, @Param("active") Boolean active, Sort sort);
}
