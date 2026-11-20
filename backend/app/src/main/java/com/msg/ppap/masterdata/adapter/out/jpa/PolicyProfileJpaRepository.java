package com.msg.ppap.masterdata.adapter.out.jpa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface PolicyProfileJpaRepository extends JpaRepository<PolicyProfileEntity, UUID> {

    Optional<PolicyProfileEntity> findByTenantIdAndId(String tenantId, UUID id);

    Optional<PolicyProfileEntity> findByTenantIdAndCode(String tenantId, String code);

    List<PolicyProfileEntity> findByTenantIdOrderByCode(String tenantId);
}
