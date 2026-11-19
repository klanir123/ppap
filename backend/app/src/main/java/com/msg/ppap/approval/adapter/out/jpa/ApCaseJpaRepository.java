package com.msg.ppap.approval.adapter.out.jpa;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ApCaseJpaRepository extends JpaRepository<ApCaseEntity, UUID> {

    Optional<ApCaseEntity> findByTenantIdAndId(String tenantId, UUID id);

    Optional<ApCaseEntity> findByTenantIdAndExchangeIdAndOwnRole(String tenantId, UUID exchangeId, String role);

    boolean existsByTenantIdAndCaseNumber(String tenantId, String caseNumber);

    boolean existsByTenantIdAndPartId(String tenantId, UUID partId);

    boolean existsByTenantIdAndPartnerBpnl(String tenantId, String bpnl);

    boolean existsByTenantIdAndRelationId(String tenantId, UUID relationId);

    boolean existsByTenantIdAndLevelCode(String tenantId, String code);

    boolean existsByTenantIdAndReasonCode(String tenantId, String code);

    @Query("""
            select c from ApCaseEntity c
            where c.tenantId = :tenant
              and (:allStatuses = true or c.status in :statuses)
              and (:ownRole is null or c.ownRole = :ownRole)
              and (:partner is null or c.partnerBpnl = :partner)
              and (:overdue is null
                   or (:overdue = true and c.status in :open and c.dueDate < :today)
                   or (:overdue = false and not (c.status in :open and c.dueDate < :today)))
              and (:needsClarification is null
                   or (:needsClarification = true and c.clarificationReason is not null)
                   or (:needsClarification = false and c.clarificationReason is null))
              and (:text is null
                   or lower(c.caseNumber) like :text escape '\\'
                   or lower(coalesce(c.partNumber, '')) like :text escape '\\'
                   or lower(coalesce(c.partnerPartNumber, '')) like :text escape '\\'
                   or lower(coalesce(c.partNameDe, '')) like :text escape '\\'
                   or lower(c.partnerName) like :text escape '\\'
                   or lower(c.partnerBpnl) like :text escape '\\')
            """)
    Page<ApCaseEntity> search(
            @Param("tenant") String tenant,
            @Param("allStatuses") boolean allStatuses,
            @Param("statuses") Collection<String> statuses,
            @Param("ownRole") String ownRole,
            @Param("partner") String partner,
            @Param("overdue") Boolean overdue,
            @Param("open") Collection<String> open,
            @Param("today") LocalDate today,
            @Param("text") String text,
            @Param("needsClarification") Boolean needsClarification,
            Pageable pageable);
}
