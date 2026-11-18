package com.msg.ppap.masterdata;

import com.msg.ppap.platform.audit.AuditableEvent;
import java.time.Instant;
import java.util.Map;

/**
 * Published after every successful write to master data (create, update, deactivate, delete). Part of the module's
 * public API; recorded by the audit module.
 *
 * @param tenantId tenant BPNL
 * @param occurredAt time of the change
 * @param actor acting user, client or technical actor
 * @param action {@code CREATED}, {@code UPDATED}, {@code DEACTIVATED} or {@code DELETED}
 * @param objectType master data type, e.g. {@code PART}
 * @param objectId technical id
 * @param details business key and version after the change
 */
public record MasterdataChanged(
        String tenantId,
        Instant occurredAt,
        String actor,
        String action,
        String objectType,
        String objectId,
        Map<String, Object> details)
        implements AuditableEvent {

    /** Change actions. */
    public enum Action {
        CREATED,
        UPDATED,
        DEACTIVATED,
        DELETED
    }
}
