package com.msg.ppap.approval.adapter.out.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.Immutable;

/** Row of {@code ap_decision} (insert only; UPDATE/DELETE rejected by DB trigger). */
@Entity
@Immutable
@Table(name = "ap_decision")
class ApDecisionEntity {

    @Id
    UUID id;

    @Column(name = "tenant_id", nullable = false)
    String tenantId;

    @Column(name = "case_id", nullable = false)
    UUID caseId;

    @Column(name = "revision", nullable = false)
    int revision;

    @Column(name = "result", nullable = false)
    String result;

    @ColumnTransformer(write = "?::jsonb")
    @Column(name = "conditions", columnDefinition = "jsonb", nullable = false)
    String conditions;

    @Column(name = "condition_due_date")
    LocalDate conditionDueDate;

    @Column(name = "comment")
    String comment;

    @ColumnTransformer(write = "?::jsonb")
    @Column(name = "element_findings", columnDefinition = "jsonb", nullable = false)
    String elementFindings;

    @Column(name = "decided_at", nullable = false)
    Instant decidedAt;

    @Column(name = "decided_by", nullable = false)
    String decidedBy;

    @Column(name = "payload", nullable = false, columnDefinition = "text")
    String payload;

    @Column(name = "payload_hash", nullable = false)
    String payloadHash;

    @Column(name = "signature_alg")
    String signatureAlg;

    @Column(name = "signature_kid")
    String signatureKid;

    @Column(name = "signature_jws", columnDefinition = "text")
    String signatureJws;

    @Column(name = "signature_status", nullable = false)
    String signatureStatus;

    protected ApDecisionEntity() {}
}
