package com.msg.ppap.audit.adapter.out.jpa;

import com.msg.ppap.audit.domain.AuditRecord;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Row of the append-only {@code audit_event} table (V1, V3). */
@Entity
@Immutable
@Table(name = "audit_event")
class AuditEventEntity {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 16)
    private String tenantId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "actor", nullable = false)
    private String actor;

    @Column(name = "action", nullable = false, length = 100)
    private String action;

    @Column(name = "object_type", nullable = false, length = 100)
    private String objectType;

    @Column(name = "object_id", nullable = false)
    private String objectId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details")
    private Map<String, Object> details;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "prev_hash", length = 64)
    private String prevHash;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "hash", nullable = false, length = 64)
    private String hash;

    @Column(name = "seq", insertable = false, updatable = false)
    private Long seq;

    protected AuditEventEntity() {}

    static AuditEventEntity from(AuditRecord record) {
        var entity = new AuditEventEntity();
        entity.id = record.id();
        entity.tenantId = record.tenantId();
        entity.occurredAt = record.occurredAt();
        entity.actor = record.actor();
        entity.action = record.action();
        entity.objectType = record.objectType();
        entity.objectId = record.objectId();
        entity.details = record.details();
        entity.prevHash = record.prevHash();
        entity.hash = record.hash();
        return entity;
    }

    AuditRecord toDomain() {
        return new AuditRecord(id, tenantId, occurredAt, actor, action, objectType, objectId, details, prevHash, hash);
    }
}
