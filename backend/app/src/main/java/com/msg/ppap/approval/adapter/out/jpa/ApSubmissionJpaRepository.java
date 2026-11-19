package com.msg.ppap.approval.adapter.out.jpa;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ApSubmissionJpaRepository extends JpaRepository<ApSubmissionEntity, UUID> {

    List<ApSubmissionEntity> findByTenantIdAndCaseIdOrderByRevision(String tenantId, UUID caseId);
}
