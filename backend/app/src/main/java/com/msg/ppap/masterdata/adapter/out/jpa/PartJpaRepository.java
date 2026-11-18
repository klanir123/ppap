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

interface PartJpaRepository extends JpaRepository<PartEntity, UUID> {

    Optional<PartEntity> findByTenantIdAndId(String tenantId, UUID id);

    Optional<PartEntity> findByTenantIdAndPartNumber(String tenantId, String partNumber);

    List<PartEntity> findByTenantIdAndIdIn(String tenantId, Collection<UUID> ids);

    @Query("""
            select p from PartEntity p
            where p.tenantId = :tenant
              and (:active is null or p.active = :active)
              and (:text is null
                   or lower(p.partNumber) like :text escape '\\'
                   or lower(p.nameDe) like :text escape '\\'
                   or lower(coalesce(p.nameEn, '')) like :text escape '\\'
                   or lower(coalesce(p.drawingNumber, '')) like :text escape '\\'
                   or lower(coalesce(p.material, '')) like :text escape '\\')
              and (:partner is null or exists (
                   select 1 from PartMappingEntity m, RelationEntity r
                   where m.tenantId = :tenant and r.tenantId = :tenant
                     and m.partId = p.id and r.id = m.relationId and r.partnerBpnl = :partner))
            """)
    Page<PartEntity> search(
            @Param("tenant") String tenant,
            @Param("text") String text,
            @Param("active") Boolean active,
            @Param("partner") String partnerBpnl,
            Pageable pageable);
}
