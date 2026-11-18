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

interface PartMappingJpaRepository extends JpaRepository<PartMappingEntity, UUID> {

    Optional<PartMappingEntity> findByTenantIdAndId(String tenantId, UUID id);

    Optional<PartMappingEntity> findByTenantIdAndRelationIdAndPartId(String tenantId, UUID relationId, UUID partId);

    Optional<PartMappingEntity> findByTenantIdAndRelationIdAndPartnerPartNumber(
            String tenantId, UUID relationId, String partnerPartNumber);

    boolean existsByTenantIdAndRelationId(String tenantId, UUID relationId);

    boolean existsByTenantIdAndPartId(String tenantId, UUID partId);

    @Query("""
            select count(m) > 0 from PartMappingEntity m
            where m.tenantId = :tenant and (m.ownSiteBpns = :bpns or m.partnerSiteBpns = :bpns)
            """)
    boolean existsBySite(@Param("tenant") String tenant, @Param("bpns") String bpns);

    @Query("""
            select m.relationId, count(m) from PartMappingEntity m
            where m.tenantId = :tenant and m.relationId in :ids
            group by m.relationId
            """)
    List<Object[]> countByRelations(@Param("tenant") String tenant, @Param("ids") Collection<UUID> ids);

    @Query("""
            select m from PartMappingEntity m
            where m.tenantId = :tenant and m.relationId = :relation
              and (:text is null
                   or lower(m.partnerPartNumber) like :text escape '\\'
                   or exists (select 1 from PartEntity p
                              where p.tenantId = :tenant and p.id = m.partId
                                and (lower(p.partNumber) like :text escape '\\'
                                     or lower(p.nameDe) like :text escape '\\')))
            """)
    Page<PartMappingEntity> search(
            @Param("tenant") String tenant,
            @Param("relation") UUID relationId,
            @Param("text") String text,
            Pageable pageable);
}
