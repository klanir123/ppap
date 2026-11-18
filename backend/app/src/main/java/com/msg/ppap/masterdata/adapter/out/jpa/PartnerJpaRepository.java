package com.msg.ppap.masterdata.adapter.out.jpa;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface PartnerJpaRepository extends JpaRepository<PartnerEntity, UUID> {

    Optional<PartnerEntity> findByTenantIdAndId(String tenantId, UUID id);

    Optional<PartnerEntity> findByTenantIdAndBpnl(String tenantId, String bpnl);

    List<PartnerEntity> findByTenantIdAndBpnlIn(String tenantId, Collection<String> bpnls);

    @Query("""
            select p from PartnerEntity p
            where p.tenantId = :tenant
              and (:active is null or p.active = :active)
              and (:text is null
                   or lower(p.bpnl) like :text escape '\\'
                   or lower(p.name) like :text escape '\\'
                   or lower(coalesce(p.contactEmail, '')) like :text escape '\\')
            """)
    Page<PartnerEntity> search(
            @Param("tenant") String tenant,
            @Param("text") String text,
            @Param("active") Boolean active,
            Pageable pageable);
}
