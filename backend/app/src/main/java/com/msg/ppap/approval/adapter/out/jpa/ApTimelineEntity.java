package com.msg.ppap.approval.adapter.out.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/** Row of {@code ap_timeline_entry} (append only). */
@Entity
@Immutable
@Table(name = "ap_timeline_entry")
class ApTimelineEntity {

    @Id
    UUID id;

    @Column(name = "tenant_id", nullable = false)
    String tenantId;

    @Column(name = "case_id", nullable = false)
    UUID caseId;

    @Column(name = "seq", insertable = false, updatable = false)
    Long seq;

    @Column(name = "at", nullable = false)
    Instant at;

    @Column(name = "actor", nullable = false)
    String actor;

    @Column(name = "from_status")
    String fromStatus;

    @Column(name = "to_status", nullable = false)
    String toStatus;

    @Column(name = "action", nullable = false)
    String action;

    @Column(name = "comment")
    String comment;

    protected ApTimelineEntity() {}
}
