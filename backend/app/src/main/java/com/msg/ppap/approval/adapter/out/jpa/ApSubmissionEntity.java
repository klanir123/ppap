package com.msg.ppap.approval.adapter.out.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.ColumnTransformer;

/**
 * Row of {@code ap_submission}. Submitted rows are immutable (DB trigger); only the signature verification status may
 * change. The canonical payload is stored as TEXT to keep its exact bytes.
 */
@Entity
@Table(name = "ap_submission")
class ApSubmissionEntity {

    @Id
    UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false, length = 16)
    String tenantId;

    @Column(name = "case_id", nullable = false, updatable = false)
    UUID caseId;

    @Column(name = "revision", nullable = false, updatable = false)
    int revision;

    @Column(name = "status", nullable = false)
    String status;

    @Column(name = "based_on_revision", updatable = false)
    Integer basedOnRevision;

    @ColumnTransformer(write = "?::jsonb")
    @Column(name = "elements", columnDefinition = "jsonb", nullable = false)
    String elements;

    @Column(name = "submitted_at")
    Instant submittedAt;

    @Column(name = "submitted_by")
    String submittedBy;

    @Column(name = "payload", columnDefinition = "text")
    String payload;

    @Column(name = "payload_hash")
    String payloadHash;

    @Column(name = "signature_alg")
    String signatureAlg;

    @Column(name = "signature_kid")
    String signatureKid;

    @Column(name = "signature_jws", columnDefinition = "text")
    String signatureJws;

    @Column(name = "signature_status")
    String signatureStatus;

    @Column(name = "exchange_mode")
    String exchangeMode;

    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;

    protected ApSubmissionEntity() {}
}
