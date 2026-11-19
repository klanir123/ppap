package com.msg.ppap.exchange.application;

import com.msg.ppap.exchange.application.port.out.DataPlaneResponse;
import com.msg.ppap.exchange.application.port.out.DataspaceConnector;
import com.msg.ppap.exchange.application.port.out.DataspaceException;
import com.msg.ppap.exchange.application.port.out.EndpointDataReference;
import com.msg.ppap.exchange.application.port.out.ExchangeMessageStore.StoredMessage;
import com.msg.ppap.exchange.application.port.out.PartnerEndpoint;
import com.msg.ppap.exchange.config.ExchangeProperties;
import com.msg.ppap.exchange.domain.ExchangeRecord;
import com.msg.ppap.exchange.domain.MessageStatus;
import com.msg.ppap.exchange.domain.RetryPolicy;
import com.msg.ppap.exchange.domain.StepName;
import com.msg.ppap.exchange.domain.StepStatus;
import com.msg.ppap.platform.web.CorrelationIdFilter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * Delivery worker of the outbox: claims due messages ({@code SKIP LOCKED}, in order per exchange id), obtains an EDR
 * for the partner's notification asset (cached EDRs are reused) and posts the envelope to the partner's data plane.
 * Failures are retried with exponential backoff (1 s … 15 min, max. 20 attempts) and then end as {@code FAILED}.
 */
@Service
public class OutboundDeliveryService {

    private static final Logger LOG = LoggerFactory.getLogger(OutboundDeliveryService.class);
    static final String CONTENT_TYPE = "application/json";

    private final ExchangeContext context;
    private final DataspaceConnector connector;
    private final PartnerDirectory partners;
    private final ExchangeProperties properties;
    private final RetryPolicy retryPolicy;
    private final Executor executor;
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicBoolean again = new AtomicBoolean();

    public OutboundDeliveryService(
            ExchangeContext context,
            DataspaceConnector connector,
            PartnerDirectory partners,
            ExchangeProperties properties,
            @Qualifier("applicationTaskExecutor") Executor executor) {
        this.context = context;
        this.connector = connector;
        this.partners = partners;
        this.properties = properties;
        this.retryPolicy =
                new RetryPolicy(properties.initialBackoff(), properties.maxBackoff(), properties.maxAttempts());
        this.executor = executor;
    }

    /** Triggers an asynchronous worker run (e.g. after a commit that queued a message). */
    public void nudge() {
        executor.execute(this::deliverDue);
    }

    /**
     * Delivers all due messages (one run at a time per instance; concurrent instances are separated by row locks).
     *
     * @return number of processed messages
     */
    public int deliverDue() {
        if (!running.compareAndSet(false, true)) {
            again.set(true);
            return 0;
        }
        int total = 0;
        try {
            do {
                again.set(false);
                List<StoredMessage> claimed;
                do {
                    Instant now = context.now();
                    claimed = context.store().claimOutbound(properties.batchSize(), now, now.plus(properties.lease()));
                    claimed.forEach(this::deliver);
                    total += claimed.size();
                } while (!claimed.isEmpty());
            } while (again.get());
        } catch (RuntimeException ex) {
            LOG.warn("Outbox worker run failed: {}", ex.toString());
        } finally {
            running.set(false);
        }
        return total;
    }

    /** Delivers one claimed message and records the outcome. */
    void deliver(StoredMessage message) {
        ExchangeRecord r = message.record();
        String previous = MDC.get(CorrelationIdFilter.MDC_KEY);
        MDC.put(CorrelationIdFilter.MDC_KEY, r.correlationId());
        int attempt = r.attempts();
        context.live().exchangeChanged(r.caseId(), r.messageId(), MessageStatus.IN_PROGRESS.name());
        try {
            PartnerEndpoint partner = partners.endpoint(r.partnerBpnl(), StepName.CATALOG);
            EndpointDataReference edr = connector.obtainEndpoint(
                    partner,
                    ExchangeAssets.NOTIFICATION_ASSET,
                    (step, status, duration, details) -> context.step(
                            r.id(),
                            r.caseId(),
                            r.messageId(),
                            MessageStatus.IN_PROGRESS,
                            attempt,
                            step,
                            status,
                            duration,
                            details));
            long start = System.nanoTime();
            DataPlaneResponse response = connector.call(
                    edr,
                    "POST",
                    message.envelope().getBytes(StandardCharsets.UTF_8),
                    CONTENT_TYPE,
                    Map.of(CorrelationIdFilter.HEADER, r.correlationId()));
            Duration duration = Duration.ofNanos(System.nanoTime() - start);
            var details = new LinkedHashMap<String, Object>();
            details.put("httpStatus", response.status());
            details.put("transferProcessId", edr.transferProcessId());
            details.put("cachedEdr", edr.cached());
            if (response.successful()) {
                step(r, attempt, StepStatus.OK, duration, details);
                complete(r);
                return;
            }
            details.put("error", response.bodySnippet());
            step(r, attempt, StepStatus.FAILED, duration, details);
            if (response.status() == 401 || response.status() == 403) {
                // token or agreement no longer accepted by the provider: negotiate anew on the next attempt
                connector.invalidateEndpoint(partner, ExchangeAssets.NOTIFICATION_ASSET);
                retryOrFail(r, "DELIVERY_UNAUTHORIZED", "Data plane answered " + response.status());
            } else if (response.status() == 400 || response.status() == 422) {
                fail(r, "DELIVERY_REJECTED", "Partner rejected the message: " + response.bodySnippet());
            } else {
                retryOrFail(r, "DELIVERY_FAILED", "Data plane answered " + response.status());
            }
        } catch (DataspaceException ex) {
            if (ex instanceof PartnerNotReadyException) {
                step(r, attempt, StepName.CATALOG, StepStatus.FAILED, Map.of("error", ex.getMessage()));
            }
            if (ex.retryable()) {
                retryOrFail(r, "EDC_" + ex.step(), ex.getMessage());
            } else {
                fail(r, "EDC_" + ex.step(), ex.getMessage());
            }
        } catch (RuntimeException ex) {
            LOG.warn("Delivery of {} failed unexpectedly", r.messageId(), ex);
            retryOrFail(r, "DELIVERY_ERROR", ex.toString());
        } finally {
            if (previous == null) {
                MDC.remove(CorrelationIdFilter.MDC_KEY);
            } else {
                MDC.put(CorrelationIdFilter.MDC_KEY, previous);
            }
        }
    }

    private void step(ExchangeRecord r, int attempt, StepStatus status, Duration duration, Map<String, Object> d) {
        context.step(
                r.id(),
                r.caseId(),
                r.messageId(),
                MessageStatus.IN_PROGRESS,
                attempt,
                StepName.DELIVERY,
                status,
                duration,
                d);
    }

    private void step(ExchangeRecord r, int attempt, StepName name, StepStatus status, Map<String, Object> d) {
        context.step(
                r.id(), r.caseId(), r.messageId(), MessageStatus.IN_PROGRESS, attempt, name, status, Duration.ZERO, d);
    }

    private void complete(ExchangeRecord r) {
        Instant now = context.now();
        context.store().updateStatus(r.id(), MessageStatus.DELIVERED, null, null, null, now, r.caseId(), null);
        context.live().exchangeChanged(r.caseId(), r.messageId(), MessageStatus.DELIVERED.name());
        LOG.info("Delivered {} {} to {} (attempt {})", r.messageType(), r.messageId(), r.partnerBpnl(), r.attempts());
    }

    private void retryOrFail(ExchangeRecord r, String code, String error) {
        Optional<Instant> next = retryPolicy.nextAttempt(r.attempts(), context.now());
        if (next.isEmpty()) {
            fail(r, code, error);
            return;
        }
        context.store().updateStatus(r.id(), MessageStatus.PENDING, next.get(), code, error, null, r.caseId(), null);
        context.live().exchangeChanged(r.caseId(), r.messageId(), MessageStatus.PENDING.name());
        LOG.warn(
                "Delivery of {} {} to {} failed (attempt {}), retry at {}: {}",
                r.messageType(),
                r.messageId(),
                r.partnerBpnl(),
                r.attempts(),
                next.get(),
                error);
    }

    private void fail(ExchangeRecord r, String code, String error) {
        context.store().updateStatus(r.id(), MessageStatus.FAILED, null, code, error, context.now(), r.caseId(), null);
        context.live().exchangeChanged(r.caseId(), r.messageId(), MessageStatus.FAILED.name());
        LOG.error("Delivery of {} {} to {} given up: {}", r.messageType(), r.messageId(), r.partnerBpnl(), error);
    }
}
