package com.msg.ppap.approval.adapter.out.jpa;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ApTimelineJpaRepository extends JpaRepository<ApTimelineEntity, UUID> {

    List<ApTimelineEntity> findByTenantIdAndCaseIdOrderBySeq(String tenantId, UUID caseId);
}
