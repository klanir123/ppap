package com.msg.ppap.platform.audit;

import java.time.Instant;
import java.util.Map;

/**
 * Domain event that must be recorded in the audit log. Modules publish events implementing this interface; the
 * {@code audit} module persists them without knowing the publishing module.
 *
 * <p>Implementations must be serializable by the Spring Modulith event publication registry (records with simple
 * types).
 */
public interface AuditableEvent {

    /** BPNL of the tenant. */
    String tenantId();

    /** When the change happened. */
    Instant occurredAt();

    /** User name, client id or technical principal (e.g. {@code system:seed}). */
    String actor();

    /** Action, e.g. {@code CREATED}, {@code UPDATED}, {@code DEACTIVATED}, {@code DELETED}. */
    String action();

    /** Object type, e.g. {@code PART}. */
    String objectType();

    /** Technical id of the object. */
    String objectId();

    /** Additional, event-specific details (JSON-serializable values only). */
    Map<String, Object> details();
}
