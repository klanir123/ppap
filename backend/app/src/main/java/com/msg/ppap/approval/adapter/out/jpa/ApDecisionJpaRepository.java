package com.msg.ppap.approval.adapter.out.jpa;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ApDecisionJpaRepository extends JpaRepository<ApDecisionEntity, UUID> {

    List<ApDecisionEntity> findByTenantIdAndCaseIdOrderByRevision(String tenantId, UUID caseId);
}
