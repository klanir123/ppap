package com.msg.ppap.masterdata;

import com.msg.ppap.platform.audit.AuditableEvent;
import java.time.Instant;
import java.util.Map;

/**
 * Published once after the seed loader has run; summarises the result per seed file.
 *
 * @param tenantId tenant BPNL
 * @param occurredAt completion time
 * @param actor always {@code system:seed}
 * @param action always {@code SEEDED}
 * @param objectType always {@code SEED}
 * @param objectId tenant BPNL
 * @param details per-file summary (created/updated/unchanged/failed or error)
 */
public record MasterdataSeeded(
        String tenantId,
        Instant occurredAt,
        String actor,
        String action,
        String objectType,
        String objectId,
        Map<String, Object> details)
        implements AuditableEvent {}
