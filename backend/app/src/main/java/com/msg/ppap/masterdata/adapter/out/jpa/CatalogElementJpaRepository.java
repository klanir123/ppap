package com.msg.ppap.masterdata.adapter.out.jpa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface CatalogElementJpaRepository extends JpaRepository<CatalogElementEntity, UUID> {

    Optional<CatalogElementEntity> findByTenantIdAndId(String tenantId, UUID id);

    Optional<CatalogElementEntity> findByTenantIdAndCode(String tenantId, String code);

    @Query(
            "select e from CatalogElementEntity e where e.tenantId = :tenant and (:active is null or e.active = :active)")
    List<CatalogElementEntity> list(@Param("tenant") String tenant, @Param("active") Boolean active, Sort sort);
}
