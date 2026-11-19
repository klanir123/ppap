package com.msg.ppap.exchange.application;

import com.msg.ppap.exchange.application.port.out.DataspaceException;
import com.msg.ppap.exchange.application.port.out.ExchangeMessageStore.StoredMessage;
import com.msg.ppap.exchange.application.port.out.StepListener;
import com.msg.ppap.exchange.config.ExchangeProperties;
import com.msg.ppap.exchange.domain.ExchangeRecord;
import com.msg.ppap.exchange.domain.MessageStatus;
import com.msg.ppap.exchange.domain.RecordTypes;
import com.msg.ppap.exchange.domain.RetryPolicy;
import com.msg.ppap.exchange.domain.StepName;
import com.msg.ppap.exchange.domain.StepStatus;
import com.msg.ppap.exchange.domain.StepTarget;
import com.msg.ppap.platform.web.CorrelationIdFilter;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * Worker of the pull jobs ({@code PULL_SUBMISSION}, {@code PULL_DOCUMENT}): claims due jobs ({@code SKIP LOCKED},
 * lease), runs them and records the outcome with the outbox retry policy (exponential backoff, then {@code FAILED});
 * rejected content ends as {@code REJECTED}. Every step is part of the job's exchange record (cockpit, SSE).
 */
@Service
public class PullJobs {

    private static final Logger LOG = LoggerFactory.getLogger(PullJobs.class);

    private final ExchangeContext context;
    private final SubmissionPullService submissions;
    private final DocumentTransferService documents;
    private final ExchangeProperties properties;
    private final RetryPolicy retryPolicy;
    private final Executor executor;
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicBoolean again = new AtomicBoolean();

    public PullJobs(
            ExchangeContext context,
            SubmissionPullService submissions,
            DocumentTransferService documents,
            ExchangeProperties properties,
            @Qualifier("applicationTaskExecutor") Executor executor) {
        this.context = context;
        this.submissions = submissions;
        this.documents = documents;
        this.properties = properties;
        this.retryPolicy =
                new RetryPolicy(properties.initialBackoff(), properties.maxBackoff(), properties.maxAttempts());
        this.executor = executor;
    }

    /** Triggers an asynchronous worker run (after a job was queued). */
    public void nudge() {
        executor.execute(this::runDue);
    }

    /**
     * Runs all due jobs (one run at a time per instance).
     *
     * @return number of processed jobs
     */
    public int runDue() {
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
                    claimed = context.store().claimPulls(properties.batchSize(), now, now.plus(properties.lease()));
                    claimed.forEach(this::run);
                    total += claimed.size();
                } while (!claimed.isEmpty());
            } while (again.get());
        } catch (RuntimeException ex) {
            LOG.warn("Pull worker run failed: {}", ex.toString());
        } finally {
            running.set(false);
        }
        return total;
    }

    void run(StoredMessage message) {
        ExchangeRecord r = message.record();
        String previous = MDC.get(CorrelationIdFilter.MDC_KEY);
        MDC.put(
                CorrelationIdFilter.MDC_KEY,
                r.correlationId() == null ? r.messageId().toString() : r.correlationId());
        Steps steps = new Steps(context, r);
        context.live().exchangeChanged(r.caseId(), r.messageId(), MessageStatus.IN_PROGRESS.name());
        try {
            Result result = RecordTypes.PULL_SUBMISSION.equals(r.messageType())
                    ? submissions.execute(message, steps)
                    : documents.execute(message, steps);
            complete(r, result);
        } catch (PullFailure ex) {
            switch (ex.outcome()) {
                case RETRY -> retryOrFail(r, ex.code(), ex.getMessage());
                case REJECT -> finish(r, MessageStatus.REJECTED, ex.code(), ex.getMessage());
                case FAIL -> finish(r, MessageStatus.FAILED, ex.code(), ex.getMessage());
            }
        } catch (DataspaceException ex) {
            if (ex.retryable()) {
                retryOrFail(r, "EDC_" + ex.step(), ex.getMessage());
            } else {
                finish(r, MessageStatus.FAILED, "EDC_" + ex.step(), ex.getMessage());
            }
        } catch (RuntimeException ex) {
            LOG.warn("Pull job {} {} failed unexpectedly", r.messageType(), r.id(), ex);
            retryOrFail(r, "PULL_ERROR", ex.toString());
        } finally {
            if (r.messageType().equals(RecordTypes.PULL_DOCUMENT)) {
                documents.afterRun(r.id());
            }
            if (previous == null) {
                MDC.remove(CorrelationIdFilter.MDC_KEY);
            } else {
                MDC.put(CorrelationIdFilter.MDC_KEY, previous);
            }
        }
    }

    /** Outcome of a successful job. */
    public record Result(UUID caseId, String caseNumber) {}

    private void complete(ExchangeRecord r, Result result) {
        UUID caseId = result.caseId() == null ? r.caseId() : result.caseId();
        context.store()
                .updateStatus(
                        r.id(), MessageStatus.PROCESSED, null, null, null, context.now(), caseId, result.caseNumber());
        context.live().exchangeChanged(caseId, r.messageId(), MessageStatus.PROCESSED.name());
        LOG.info("Pull {} {} from {} done (attempt {})", r.messageType(), r.id(), r.partnerBpnl(), r.attempts());
    }

    private void retryOrFail(ExchangeRecord r, String code, String error) {
        Optional<Instant> next = retryPolicy.nextAttempt(r.attempts(), context.now());
        if (next.isEmpty()) {
            finish(r, MessageStatus.FAILED, code, error);
            return;
        }
        context.store().updateStatus(r.id(), MessageStatus.PENDING, next.get(), code, error, null, r.caseId(), null);
        context.live().exchangeChanged(r.caseId(), r.messageId(), MessageStatus.PENDING.name());
        LOG.info(
                "Pull {} {} from {} failed (attempt {}), retry at {}: {}",
                r.messageType(),
                r.id(),
                r.partnerBpnl(),
                r.attempts(),
                next.get(),
                error);
    }

    private void finish(ExchangeRecord r, MessageStatus status, String code, String error) {
        context.store().updateStatus(r.id(), status, null, code, error, context.now(), r.caseId(), null);
        context.live().exchangeChanged(r.caseId(), r.messageId(), status.name());
        LOG.warn("Pull {} {} from {} ended {}: {} {}", r.messageType(), r.id(), r.partnerBpnl(), status, code, error);
    }

    /** Step recording of one job run (also usable as {@link StepListener} for EDC steps). */
    public static final class Steps implements StepListener {

        private final ExchangeContext context;
        private final ExchangeRecord record;
        private final StepTarget target;
        private final String targetRef;

        Steps(ExchangeContext context, ExchangeRecord record) {
            this(context, record, null, null);
        }

        private Steps(ExchangeContext context, ExchangeRecord record, StepTarget target, String targetRef) {
            this.context = context;
            this.record = record;
            this.target = target;
            this.targetRef = targetRef;
        }

        /** The same recorder for steps on another target (asset). */
        public Steps target(StepTarget newTarget, String ref) {
            return new Steps(context, record, newTarget, ref);
        }

        public ExchangeRecord record() {
            return record;
        }

        @Override
        public void onStep(StepName step, StepStatus status, Duration duration, Map<String, Object> details) {
            Map<String, Object> d = target == null || (details != null && details.containsKey(StepTarget.KEY))
                    ? details
                    : target.apply(details, targetRef);
            context.step(
                    record.id(),
                    record.caseId(),
                    record.messageId(),
                    MessageStatus.IN_PROGRESS,
                    Math.max(1, record.attempts()),
                    step,
                    status,
                    duration,
                    d);
        }

        public void ok(StepName step, long durationMs, Map<String, Object> details) {
            onStep(step, StepStatus.OK, Duration.ofMillis(durationMs), details);
        }

        public void failed(StepName step, long durationMs, Map<String, Object> details) {
            onStep(step, StepStatus.FAILED, Duration.ofMillis(durationMs), details);
        }
    }
}
