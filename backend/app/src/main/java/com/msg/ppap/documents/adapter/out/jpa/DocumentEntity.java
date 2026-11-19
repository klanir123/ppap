package com.msg.ppap.documents.adapter.out.jpa;

import com.msg.ppap.documents.domain.Document;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "doc_document")
class DocumentEntity {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false, length = 16)
    private String tenantId;

    @Column(name = "file_name", nullable = false, updatable = false)
    private String fileName;

    @Column(name = "media_type", nullable = false, updatable = false)
    private String mediaType;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long size;

    @Column(name = "sha256", nullable = false, updatable = false, length = 64)
    private String sha256;

    @Column(name = "storage_key", nullable = false, updatable = false)
    private String storageKey;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt;

    @Column(name = "uploaded_by", nullable = false, updatable = false)
    private String uploadedBy;

    protected DocumentEntity() {}

    DocumentEntity(Document d) {
        this.id = d.id();
        this.tenantId = d.tenantId();
        this.fileName = d.fileName();
        this.mediaType = d.mediaType();
        this.size = d.size();
        this.sha256 = d.sha256();
        this.storageKey = d.storageKey();
        this.uploadedAt = d.uploadedAt();
        this.uploadedBy = d.uploadedBy();
    }

    Document toDomain() {
        return new Document(id, tenantId, fileName, mediaType, size, sha256, uploadedAt, uploadedBy);
    }
}
