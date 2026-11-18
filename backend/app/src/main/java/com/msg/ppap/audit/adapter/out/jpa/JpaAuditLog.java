package com.msg.ppap.audit.adapter.out.jpa;

import com.msg.ppap.audit.application.port.out.AuditLog;
import com.msg.ppap.audit.domain.AuditRecord;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
class JpaAuditLog implements AuditLog {

    private final AuditEventJpaRepository jpa;
    private final EntityManager entityManager;

    JpaAuditLog(AuditEventJpaRepository jpa, EntityManager entityManager) {
        this.jpa = jpa;
        this.entityManager = entityManager;
    }

    @Override
    public void lockChain(String tenantId, String objectType, String objectId) {
        // transaction-scoped advisory lock: concurrent events of the same object are chained one after another
        entityManager
                .createNativeQuery("select 1 from pg_advisory_xact_lock(hashtextextended(?1, 0))")
                .setParameter(1, tenantId + "|" + objectType + "|" + objectId)
                .getSingleResult();
    }

    @Override
    public Optional<String> latestHash(String tenantId, String objectType, String objectId) {
        return jpa.latestHash(tenantId, objectType, objectId);
    }

    @Override
    public void append(AuditRecord record) {
        entityManager.persist(AuditEventEntity.from(record));
        entityManager.flush();
    }

    @Override
    public Page<AuditRecord> search(String tenantId, String objectType, String objectId, Pageable pageable) {
        return jpa.search(tenantId, objectType, objectId, pageable).map(AuditEventEntity::toDomain);
    }

    @Override
    public List<AuditRecord> chain(String tenantId, String objectType, String objectId) {
        return jpa.chain(tenantId, objectType, objectId).stream()
                .map(AuditEventEntity::toDomain)
                .toList();
    }
}
