package com.msg.ppap.audit.application.port.out;

import com.msg.ppap.audit.domain.AuditRecord;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Append-only storage of audit records. */
public interface AuditLog {

    /** Serializes appends to the chain of one object until the end of the transaction. */
    void lockChain(String tenantId, String objectType, String objectId);

    /** Hash of the latest record of the chain. */
    Optional<String> latestHash(String tenantId, String objectType, String objectId);

    void append(AuditRecord record);

    /** Records of a tenant, newest first; filters are optional. */
    Page<AuditRecord> search(String tenantId, String objectType, String objectId, Pageable pageable);

    /** Complete chain of an object, oldest first. */
    List<AuditRecord> chain(String tenantId, String objectType, String objectId);
}
