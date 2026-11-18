package com.msg.ppap.audit.adapter.in.events;

import com.msg.ppap.audit.application.AuditService;
import com.msg.ppap.platform.audit.AuditableEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Persists every {@link AuditableEvent} after the publishing transaction committed, in its own transaction.
 *
 * <p>Deliberately <em>synchronous</em> (not {@code @ApplicationModuleListener}, which is always {@code @Async}):
 * asynchronous dispatch may reorder events of the same object, which would break the chronological order of its
 * hash chain. Delivery is still tracked by the Spring Modulith event publication registry: a failure is logged by
 * Spring (it does not affect the committed business transaction) and the publication stays incomplete, so it is
 * republished on restart ({@code spring.modulith.events.republish-outstanding-events-on-restart}).
 */
@Component
class AuditEventListener {

    private final AuditService audit;

    AuditEventListener(AuditService audit) {
        this.audit = audit;
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void on(AuditableEvent event) {
        audit.record(event);
    }
}
