package com.msg.ppap.documents.adapter.out.jpa;

import com.msg.ppap.documents.application.port.out.DocumentRepository;
import com.msg.ppap.documents.domain.Document;
import com.msg.ppap.identity.TenantContext;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class JpaDocumentRepository implements DocumentRepository {

    private final DocumentJpaRepository jpa;
    private final TenantContext tenant;

    JpaDocumentRepository(DocumentJpaRepository jpa, TenantContext tenant) {
        this.jpa = jpa;
        this.tenant = tenant;
    }

    @Override
    public Optional<Document> findById(UUID id) {
        return jpa.findByTenantIdAndId(tenant.tenantId(), id).map(DocumentEntity::toDomain);
    }

    @Override
    public List<Document> findAllById(Collection<UUID> ids) {
        return jpa.findByTenantIdAndIdIn(tenant.tenantId(), ids).stream()
                .map(DocumentEntity::toDomain)
                .toList();
    }

    @Override
    public void insert(Document document) {
        jpa.saveAndFlush(new DocumentEntity(document));
    }
}
