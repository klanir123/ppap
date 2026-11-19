package com.msg.ppap.approval.adapter.out.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.ColumnTransformer;

/** Row of {@code ap_form_template}; the definition is a JSONB document. */
@Entity
@Table(name = "ap_form_template")
class ApFormTemplateEntity {

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

    @Column(name = "code", nullable = false)
    String code;

    @Column(name = "name_de", nullable = false)
    String nameDe;

    @Column(name = "name_en", nullable = false)
    String nameEn;

    @ColumnTransformer(write = "?::jsonb")
    @Column(name = "definition", columnDefinition = "jsonb", nullable = false)
    String definition;

    @Column(name = "active", nullable = false)
    boolean active;

    protected ApFormTemplateEntity() {}
}
