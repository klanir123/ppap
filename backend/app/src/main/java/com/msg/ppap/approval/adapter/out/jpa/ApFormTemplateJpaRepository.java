package com.msg.ppap.approval.adapter.out.jpa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ApFormTemplateJpaRepository extends JpaRepository<ApFormTemplateEntity, UUID> {

    List<ApFormTemplateEntity> findByTenantIdOrderByCode(String tenantId);

    Optional<ApFormTemplateEntity> findByTenantIdAndCode(String tenantId, String code);
}
