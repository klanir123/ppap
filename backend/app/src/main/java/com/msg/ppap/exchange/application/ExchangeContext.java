package com.msg.ppap.exchange.application;

import com.msg.ppap.exchange.application.port.out.ExchangeMessageStore;
import com.msg.ppap.exchange.domain.ExchangeStep;
import com.msg.ppap.exchange.domain.MessageStatus;
import com.msg.ppap.exchange.domain.StepName;
import com.msg.ppap.exchange.domain.StepStatus;
import com.msg.ppap.identity.TenantContext;
import com.msg.ppap.platform.web.CorrelationIdFilter;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/** Shared infrastructure of the exchange use cases: clock, tenant, step recording with live notification. */
@Component
public class ExchangeContext {

    private final ExchangeMessageStore store;
    private final LiveEventService live;
    private final TenantContext tenant;
    private final Clock clock;

    public ExchangeContext(ExchangeMessageStore store, LiveEventService live, TenantContext tenant, Clock clock) {
        this.store = store;
        this.live = live;
        this.tenant = tenant;
        this.clock = clock;
    }

    public Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }

    public String tenantBpnl() {
        return tenant.tenantId();
    }

    public ExchangeMessageStore store() {
        return store;
    }

    public LiveEventService live() {
        return live;
    }

    /** Persists a step immediately (own transaction) and notifies live subscribers. */
    public void step(
            UUID messageId,
            UUID caseId,
            UUID envelopeMessageId,
            MessageStatus status,
            int attempt,
            StepName step,
            StepStatus outcome,
            Duration duration,
            Map<String, Object> details) {
        store.addStep(messageId, new ExchangeStep(attempt, step, outcome, now(), duration.toMillis(), details));
        live.exchangeChanged(caseId, envelopeMessageId, status.name());
    }

    /** Current correlation id or a new one. */
    public static String correlationId() {
        String current = MDC.get(CorrelationIdFilter.MDC_KEY);
        return current == null || current.isBlank() ? UUID.randomUUID().toString() : current;
    }
}
