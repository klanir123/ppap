package com.msg.ppap.masterdata.adapter.out.jpa;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SiteJpaRepository extends JpaRepository<SiteEntity, UUID> {

    Optional<SiteEntity> findByTenantIdAndId(String tenantId, UUID id);

    Optional<SiteEntity> findByTenantIdAndBpns(String tenantId, String bpns);

    boolean existsByTenantIdAndOwnerBpnl(String tenantId, String ownerBpnl);

    @Query("""
            select s from SiteEntity s
            where s.tenantId = :tenant
              and (:active is null or s.active = :active)
              and (:owner is null or s.ownerBpnl = :owner)
              and (:text is null
                   or lower(s.bpns) like :text escape '\\'
                   or lower(s.name) like :text escape '\\'
                   or lower(coalesce(s.city, '')) like :text escape '\\')
            """)
    Page<SiteEntity> search(
            @Param("tenant") String tenant,
            @Param("text") String text,
            @Param("active") Boolean active,
            @Param("owner") String ownerBpnl,
            Pageable pageable);
}
