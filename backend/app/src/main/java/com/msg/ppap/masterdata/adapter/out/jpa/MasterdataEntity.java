package com.msg.ppap.masterdata.adapter.out.jpa;

import com.msg.ppap.masterdata.domain.Metadata;
import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/** Common columns of all master data tables (ADR-0004: {@code tenant_id} on every table). */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
abstract class MasterdataEntity {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false, length = 16)
    private String tenantId;

    @Version
    private Long version;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @CreatedBy
    @Column(name = "created_by", nullable = false, updatable = false)
    private String createdBy;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @LastModifiedBy
    @Column(name = "updated_by", nullable = false)
    private String updatedBy;

    protected MasterdataEntity() {}

    protected MasterdataEntity(UUID id, String tenantId) {
        this.id = id;
        this.tenantId = tenantId;
    }

    UUID getId() {
        return id;
    }

    String getTenantId() {
        return tenantId;
    }

    Metadata metadata() {
        return new Metadata(version, createdAt, createdBy, updatedAt, updatedBy);
    }
}
