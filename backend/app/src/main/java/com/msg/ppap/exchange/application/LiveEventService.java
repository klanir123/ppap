package com.msg.ppap.exchange.application;

import com.msg.ppap.identity.TenantContext;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Fan-out of live events to the subscribers of the event stream (SSE). Subscribers only receive events of their own
 * tenant; a failing subscriber (closed connection) is removed.
 */
@Service
public class LiveEventService {

    private static final Logger LOG = LoggerFactory.getLogger(LiveEventService.class);

    private final TenantContext tenant;
    private final Set<Subscription> subscriptions = ConcurrentHashMap.newKeySet();

    public LiveEventService(TenantContext tenant) {
        this.tenant = tenant;
    }

    /** Registers a subscriber of the current tenant. */
    public Subscription subscribe(Consumer<LiveEvent> subscriber) {
        var subscription = new Subscription(tenant.tenantId(), subscriber, this);
        subscriptions.add(subscription);
        return subscription;
    }

    /** {@code case.changed}. */
    public void caseChanged(Object caseId, String status) {
        publish(LiveEvent.CASE_CHANGED, Map.of("caseId", String.valueOf(caseId), "status", String.valueOf(status)));
    }

    /** {@code exchange.changed}. */
    public void exchangeChanged(Object caseId, Object messageId, String status) {
        var data = new java.util.LinkedHashMap<String, Object>();
        data.put("caseId", caseId == null ? null : caseId.toString());
        data.put("messageId", String.valueOf(messageId));
        data.put("status", status);
        publish(LiveEvent.EXCHANGE_CHANGED, data);
    }

    /**
     * {@code connector.log { at, level, correlationId, messageType?, step?, text }}: a step of an exchange record
     * (UC-10 live log, derived from {@code ExchangeRecord} steps, not from server logs).
     */
    public void connectorLog(com.msg.ppap.exchange.domain.ExchangeStepRecorded recorded) {
        var step = recorded.step();
        var data = new java.util.LinkedHashMap<String, Object>();
        data.put("at", step.at().toString());
        data.put("level", level(step.status()));
        data.put("correlationId", recorded.correlationId());
        data.put("messageType", recorded.messageType());
        data.put("step", step.step().name());
        data.put("status", step.status().name());
        data.put("direction", recorded.direction().name());
        data.put("partnerBpnl", recorded.partnerBpnl());
        data.put("caseId", recorded.caseId() == null ? null : recorded.caseId().toString());
        data.put(
                "messageId",
                recorded.messageId() == null ? null : recorded.messageId().toString());
        data.put("text", text(recorded));
        publish(LiveEvent.CONNECTOR_LOG, data);
    }

    static String level(com.msg.ppap.exchange.domain.StepStatus status) {
        return switch (status) {
            case OK -> "INFO";
            case SKIPPED -> "DEBUG";
            case FAILED -> "ERROR";
        };
    }

    static String text(com.msg.ppap.exchange.domain.ExchangeStepRecorded recorded) {
        var step = recorded.step();
        var text = new StringBuilder();
        text.append(recorded.direction() == com.msg.ppap.exchange.domain.Direction.OUTBOUND ? "→ " : "← ")
                .append(recorded.partnerBpnl())
                .append(' ')
                .append(recorded.messageType())
                .append(": ")
                .append(step.step().name())
                .append(' ')
                .append(step.status().name())
                .append(" (")
                .append(step.durationMs())
                .append(" ms)");
        for (String key : java.util.List.of("assetId", "httpStatus", "targetRef", "error")) {
            Object value = step.details().get(key);
            if (value != null && !String.valueOf(value).isBlank()) {
                text.append(", ").append(key).append('=').append(value);
            }
        }
        return text.toString();
    }

    /** Heartbeat to all subscribers (keeps proxies from closing idle connections). */
    public void heartbeat() {
        publish(LiveEvent.HEARTBEAT, Map.of("at", Instant.now().toString()));
    }

    /** Number of open subscriptions. */
    public int subscribers() {
        return subscriptions.size();
    }

    /**
     * Publishes an event to the subscribers of its tenant – <b>after commit</b> of the current transaction, so that a
     * client refetching on the event reads the committed state; immediately if no transaction is active. Delivery
     * happens in {@code afterCompletion(COMMITTED)}: Spring re-reads the synchronizations for that phase, so events
     * published from {@code afterCommit} callbacks are delivered too; inside {@code afterCompletion} (e.g. {@code
     * AFTER_COMMIT} event listeners) synchronization is no longer active and events go out directly. Events of a
     * rolled-back transaction are dropped.
     */
    void publish(String type, Map<String, Object> data) {
        var event = new LiveEvent(tenant.tenantId(), type, data);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == STATUS_COMMITTED) {
                        deliver(event);
                    }
                }
            });
            return;
        }
        deliver(event);
    }

    private void deliver(LiveEvent event) {
        for (Subscription subscription : subscriptions) {
            if (!subscription.tenantId().equals(event.tenantId())) {
                continue;
            }
            try {
                subscription.subscriber().accept(event);
            } catch (RuntimeException ex) {
                LOG.debug("Removing live event subscriber: {}", ex.toString());
                subscriptions.remove(subscription);
            }
        }
    }

    /** An open subscription; {@link #close()} removes it. */
    public record Subscription(String tenantId, Consumer<LiveEvent> subscriber, LiveEventService owner)
            implements AutoCloseable {

        @Override
        public void close() {
            owner.subscriptions.remove(this);
        }

        @Override
        public boolean equals(Object other) {
            return this == other;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(this);
        }
    }
}
