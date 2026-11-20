package com.msg.ppap.approval.adapter.out.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.ColumnTransformer;

/** Row of {@code ap_case}; request elements, attachments and review findings as JSONB documents. */
@Entity
@Table(name = "ap_case")
class ApCaseEntity {

    @Id
    UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false, length = 16)
    String tenantId;

    @Version
    Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt;

    @Column(name = "created_by", nullable = false, updatable = false)
    String createdBy;

    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;

    @Column(name = "updated_by", nullable = false)
    String updatedBy;

    @Column(name = "case_number", nullable = false, updatable = false)
    String caseNumber;

    @Column(name = "exchange_id", nullable = false, updatable = false)
    UUID exchangeId;

    @Column(name = "own_role", nullable = false, updatable = false)
    String ownRole;

    @Column(name = "source", nullable = false, updatable = false)
    String source;

    @Column(name = "status", nullable = false)
    String status;

    @Column(name = "partner_bpnl", nullable = false)
    String partnerBpnl;

    @Column(name = "partner_name", nullable = false)
    String partnerName;

    @Column(name = "relation_id")
    UUID relationId;

    @Column(name = "part_id")
    UUID partId;

    @Column(name = "part_number")
    String partNumber;

    @Column(name = "parent_case_id", updatable = false)
    UUID parentCaseId;

    @Column(name = "clarification_reason")
    String clarificationReason;

    @Column(name = "clarification_partner_part_number")
    String clarificationPartnerPartNumber;

    @Column(name = "part_name_de")
    String partNameDe;

    @Column(name = "part_name_en")
    String partNameEn;

    @Column(name = "partner_part_number")
    String partnerPartNumber;

    @Column(name = "reason_code", nullable = false)
    String reasonCode;

    @Column(name = "reason_name_de", nullable = false)
    String reasonNameDe;

    @Column(name = "reason_name_en", nullable = false)
    String reasonNameEn;

    @Column(name = "level_code", nullable = false)
    String levelCode;

    @Column(name = "level_name_de", nullable = false)
    String levelNameDe;

    @Column(name = "level_name_en", nullable = false)
    String levelNameEn;

    @Column(name = "due_date", nullable = false)
    LocalDate dueDate;

    @Column(name = "remarks")
    String remarks;

    @Column(name = "external_reference")
    String externalReference;

    @Column(name = "requested_at")
    Instant requestedAt;

    @Column(name = "requested_by")
    String requestedBy;

    @ColumnTransformer(write = "?::jsonb")
    @Column(name = "request_elements", columnDefinition = "jsonb", nullable = false)
    String requestElements;

    @ColumnTransformer(write = "?::jsonb")
    @Column(name = "request_attachments", columnDefinition = "jsonb", nullable = false)
    String requestAttachments;

    @ColumnTransformer(write = "?::jsonb")
    @Column(name = "review_findings", columnDefinition = "jsonb", nullable = false)
    String reviewFindings;

    @Column(name = "current_revision")
    Integer currentRevision;

    @Column(name = "submitted_at")
    Instant submittedAt;

    @Column(name = "decided_at")
    Instant decidedAt;

    @Column(name = "last_decision")
    String lastDecision;

    protected ApCaseEntity() {}
}
