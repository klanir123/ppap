package com.msg.ppap.masterdata.adapter.out.jpa;

import com.msg.ppap.masterdata.domain.PartnerRole;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface RelationJpaRepository extends JpaRepository<RelationEntity, UUID> {

    Optional<RelationEntity> findByTenantIdAndId(String tenantId, UUID id);

    Optional<RelationEntity> findByTenantIdAndPartnerBpnlAndPartnerRole(
            String tenantId, String partnerBpnl, PartnerRole partnerRole);

    boolean existsByTenantIdAndPartnerBpnl(String tenantId, String partnerBpnl);

    @Query("""
            select r from RelationEntity r
            where r.tenantId = :tenant
              and (:active is null or r.active = :active)
              and (:partner is null or r.partnerBpnl = :partner)
              and (:role is null or r.partnerRole = :role)
              and (:text is null
                   or lower(r.partnerBpnl) like :text escape '\\'
                   or exists (select 1 from PartnerEntity p
                              where p.tenantId = :tenant and p.bpnl = r.partnerBpnl
                                and lower(p.name) like :text escape '\\'))
            """)
    Page<RelationEntity> search(
            @Param("tenant") String tenant,
            @Param("text") String text,
            @Param("active") Boolean active,
            @Param("partner") String partnerBpnl,
            @Param("role") PartnerRole role,
            Pageable pageable);

    boolean existsByTenantIdAndPolicyProfile(String tenantId, String policyProfile);

    java.util.List<RelationEntity> findByTenantIdOrderByPartnerBpnlAscPartnerRoleAsc(String tenantId);
}
