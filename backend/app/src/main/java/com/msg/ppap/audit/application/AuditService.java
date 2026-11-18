package com.msg.ppap.audit.application;

import com.msg.ppap.audit.application.port.out.AuditLog;
import com.msg.ppap.audit.domain.AuditRecord;
import com.msg.ppap.identity.TenantContext;
import com.msg.ppap.platform.audit.AuditableEvent;
import com.msg.ppap.platform.domain.Ids;
import java.util.List;
import java.util.Objects;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Records auditable events in the hash-chained audit log and serves the audit trail. */
@Service
public class AuditService {

    private final AuditLog log;
    private final TenantContext tenant;

    public AuditService(AuditLog log, TenantContext tenant) {
        this.log = log;
        this.tenant = tenant;
    }

    /** Appends the event to the chain of its object. */
    @Transactional
    public AuditRecord record(AuditableEvent event) {
        log.lockChain(event.tenantId(), event.objectType(), event.objectId());
        String prevHash = log.latestHash(event.tenantId(), event.objectType(), event.objectId())
                .orElse(null);
        AuditRecord record = AuditRecord.append(
                Ids.newId(),
                event.tenantId(),
                event.occurredAt(),
                event.actor(),
                event.action(),
                event.objectType(),
                event.objectId(),
                event.details(),
                prevHash);
        log.append(record);
        return record;
    }

    /** Audit trail of the current tenant, newest first. */
    @Transactional(readOnly = true)
    public Page<AuditRecord> search(String objectType, String objectId, Pageable pageable) {
        return log.search(tenant.tenantId(), blankToNull(objectType), blankToNull(objectId), pageable);
    }

    /** Verifies hashes and links of an object's chain. */
    @Transactional(readOnly = true)
    public boolean verifyChain(String objectType, String objectId) {
        List<AuditRecord> chain = log.chain(tenant.tenantId(), objectType, objectId);
        String previous = null;
        for (AuditRecord record : chain) {
            if (!record.hashValid() || !Objects.equals(previous, record.prevHash())) {
                return false;
            }
            previous = record.hash();
        }
        return true;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
