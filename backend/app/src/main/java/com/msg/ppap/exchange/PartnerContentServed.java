package com.msg.ppap.exchange;

import com.msg.ppap.platform.audit.AuditableEvent;
import java.time.Instant;
import java.util.Map;

/**
 * Published when the own data plane served content to a partner (submodel {@code PpapSubmission}, document, public
 * keys): access evidence in the audit log with action {@value #ACTION} (docs/api/cascade-transparency-api.md §4).
 *
 * @param actor {@code partner:<BPNL>} of the consumer (from the data plane header {@code Edc-Bpn})
 * @param objectType {@code APPROVAL_CASE} (submodel), {@code DOCUMENT} or {@code PUBLIC_KEYS}
 * @param objectId case id, document id or own BPNL
 * @param details partner, agreement id and what was served (revision, payload hash, SHA-256, key ids)
 */
public record PartnerContentServed(
        String tenantId,
        Instant occurredAt,
        String actor,
        String objectType,
        String objectId,
        Map<String, Object> details)
        implements AuditableEvent {

    /** Audit action. */
    public static final String ACTION = "SERVED_TO_PARTNER";

    /** Object type of served public keys. */
    public static final String PUBLIC_KEYS = "PUBLIC_KEYS";

    @Override
    public String action() {
        return ACTION;
    }
}
