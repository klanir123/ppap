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

    /** Heartbeat to all subscribers (keeps proxies from closing idle connections). */
    public void heartbeat() {
        publish(LiveEvent.HEARTBEAT, Map.of("at", Instant.now().toString()));
    }

    /** Number of open subscriptions. */
    public int subscribers() {
        return subscriptions.size();
    }

    void publish(String type, Map<String, Object> data) {
        var event = new LiveEvent(tenant.tenantId(), type, data);
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
