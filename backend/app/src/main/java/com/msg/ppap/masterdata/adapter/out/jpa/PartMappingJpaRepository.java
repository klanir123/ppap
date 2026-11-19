package com.msg.ppap.masterdata.adapter.out.jpa;

import com.msg.ppap.masterdata.domain.PartnerRole;
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

    List<PartMappingEntity> findByTenantIdAndPartId(String tenantId, UUID partId);

    @Query("""
            select m from PartMappingEntity m
            where m.tenantId = :tenant
              and exists (select 1 from RelationEntity r
                          where r.tenantId = :tenant and r.id = m.relationId and r.active = true
                            and (:role is null or r.partnerRole = :role))
              and (:text is null
                   or lower(m.partnerPartNumber) like :text escape '\\'
                   or exists (select 1 from PartEntity p
                              where p.tenantId = :tenant and p.id = m.partId
                                and (lower(p.partNumber) like :text escape '\\'
                                     or lower(p.nameDe) like :text escape '\\'))
                   or exists (select 1 from RelationEntity r2, PartnerEntity pa
                              where r2.tenantId = :tenant and r2.id = m.relationId
                                and pa.tenantId = :tenant and pa.bpnl = r2.partnerBpnl
                                and (lower(pa.name) like :text escape '\\' or lower(pa.bpnl) like :text escape '\\')))
            """)
    Page<PartMappingEntity> searchAll(
            @Param("tenant") String tenant,
            @Param("role") PartnerRole role,
            @Param("text") String text,
            Pageable pageable);

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
