package com.msg.ppap.exchange.application;

import com.msg.ppap.approval.application.port.in.InboundMessage;
import com.msg.ppap.approval.application.port.in.InboundResult;
import com.msg.ppap.approval.application.port.in.PartnerSharingUseCase;
import com.msg.ppap.approval.application.port.in.ReceiveDecisionUseCase;
import com.msg.ppap.approval.application.port.in.ReceiveRequestUseCase;
import com.msg.ppap.approval.application.port.in.ReceiveStatusUpdateUseCase;
import com.msg.ppap.approval.application.port.in.ReceiveSubmissionUseCase;
import com.msg.ppap.approval.application.port.in.ReceiveWithdrawalUseCase;
import com.msg.ppap.exchange.application.port.out.DataspaceException;
import com.msg.ppap.exchange.application.port.out.ExchangeMessageStore.NewMessage;
import com.msg.ppap.exchange.application.port.out.ExchangeMessageStore.StoredMessage;
import com.msg.ppap.exchange.config.ExchangeProperties;
import com.msg.ppap.exchange.domain.Direction;
import com.msg.ppap.exchange.domain.ExchangeRecord;
import com.msg.ppap.exchange.domain.InboundRejection;
import com.msg.ppap.exchange.domain.MessageStatus;
import com.msg.ppap.exchange.domain.StepName;
import com.msg.ppap.exchange.domain.StepStatus;
import com.msg.ppap.exchange.domain.StepTarget;
import com.msg.ppap.identity.SignatureVerifier;
import com.msg.ppap.platform.domain.FieldViolation;
import com.msg.ppap.platform.domain.Ids;
import com.msg.ppap.platform.domain.NotFoundException;
import com.msg.ppap.platform.domain.PpapException;
import com.msg.ppap.platform.web.CorrelationIdFilter;
import com.msg.ppap.semantics.api.ExchangeCodec;
import com.msg.ppap.semantics.api.Notification;
import com.msg.ppap.semantics.api.NotificationCodec;
import com.msg.ppap.semantics.api.PayloadValidationException;
import com.msg.ppap.semantics.api.SubmissionAvailableMessage;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Inbox: received notifications are stored unchanged first (idempotent by sender and message id), then processed
 * asynchronously – origin checks, payload hash, partner key, dispatch to the {@code Receive*UseCase}s of the approval
 * module. Business rejections end as {@code REJECTED} with a rejection code (the sender got its 2xx already).
 */
@Service
public class InboxService {

    private static final Logger LOG = LoggerFactory.getLogger(InboxService.class);
    static final String UNKNOWN = "UNKNOWN";

    private final ExchangeContext context;
    private final NotificationCodec codec;
    private final PartnerDirectory partners;
    private final PartnerKeyService partnerKeys;
    private final SignatureVerifier verifier;
    private final ReceiveRequestUseCase requests;
    private final ReceiveWithdrawalUseCase withdrawals;
    private final ReceiveStatusUpdateUseCase statusUpdates;
    private final ReceiveSubmissionUseCase submissions;
    private final ReceiveDecisionUseCase decisions;
    private final ExchangeProperties properties;
    private final TransactionTemplate transaction;
    private final Executor executor;
    private final PullCollaborators pull;

    /**
     * Phase 5 collaborators: announced submissions are pulled, documents of received requests and submissions are
     * transferred.
     */
    public record PullCollaborators(
            ExchangeCodec codec,
            PartnerSharingUseCase sharing,
            SubmissionPullService submissions,
            DocumentTransferService documents,
            PullJobs jobs) {}

    @SuppressWarnings("java:S107") // one port per message type
    public InboxService(
            ExchangeContext context,
            NotificationCodec codec,
            PartnerDirectory partners,
            PartnerKeyService partnerKeys,
            SignatureVerifier verifier,
            ReceiveRequestUseCase requests,
            ReceiveWithdrawalUseCase withdrawals,
            ReceiveStatusUpdateUseCase statusUpdates,
            ReceiveSubmissionUseCase submissions,
            ReceiveDecisionUseCase decisions,
            ExchangeProperties properties,
            PlatformTransactionManager transactionManager,
            @Qualifier("applicationTaskExecutor") Executor executor,
            ExchangeCodec exchangeCodec,
            PartnerSharingUseCase sharing,
            SubmissionPullService submissionPulls,
            DocumentTransferService documentTransfers,
            PullJobs pullJobs) {
        this.pull = new PullCollaborators(exchangeCodec, sharing, submissionPulls, documentTransfers, pullJobs);
        this.context = context;
        this.codec = codec;
        this.partners = partners;
        this.partnerKeys = partnerKeys;
        this.verifier = verifier;
        this.requests = requests;
        this.withdrawals = withdrawals;
        this.statusUpdates = statusUpdates;
        this.submissions = submissions;
        this.decisions = decisions;
        this.properties = properties;
        this.transaction = new TransactionTemplate(transactionManager);
        this.executor = executor;
    }

    /** Outcome of accepting a delivery. */
    public sealed interface Receipt {

        /** Stored, processed asynchronously (202). */
        record Accepted(UUID messageId) implements Receipt {}

        /** Already received (200, no effect). */
        record Duplicate(UUID messageId) implements Receipt {}

        /** Envelope or payload invalid (400). */
        record Invalid(List<FieldViolation> violations) implements Receipt {}
    }

    /**
     * Stores a delivery of the own data plane.
     *
     * @param body request body as received
     * @param edcBpn consumer BPN forwarded by the data plane ({@code Edc-Bpn}), if any
     * @param agreementId contract agreement forwarded by the data plane, if any
     */
    public Receipt accept(byte[] body, String edcBpn, String agreementId) {
        Instant now = context.now();
        Notification notification;
        try {
            notification = codec.decode(body);
        } catch (PayloadValidationException ex) {
            storeInvalid(body, edcBpn, agreementId, ex, now);
            return new Receipt.Invalid(ex.violations());
        }
        Notification.Header header = notification.header();
        UUID id = Ids.newId();
        var stored = transaction.execute(status -> {
            boolean inserted = context.store()
                    .insertInbound(
                            new NewMessage(
                                    id,
                                    header.messageId(),
                                    Direction.INBOUND,
                                    notification.messageType().name(),
                                    header.senderBpn(),
                                    partners.name(header.senderBpn()).orElse(null),
                                    null,
                                    null,
                                    notification.exchangeId(),
                                    notification.payloadHash(),
                                    notification.correlationId(),
                                    new String(body, StandardCharsets.UTF_8),
                                    edcBpn,
                                    now),
                            MessageStatus.RECEIVED,
                            null,
                            null);
            if (inserted) {
                var details = new LinkedHashMap<String, Object>();
                details.put("size", body.length);
                put(details, "edcBpn", edcBpn);
                put(details, "agreementId", agreementId);
                context.store()
                        .addStep(
                                id,
                                new com.msg.ppap.exchange.domain.ExchangeStep(
                                        1, StepName.RECEIPT, StepStatus.OK, now, 0, details));
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        context.live().exchangeChanged(null, header.messageId(), MessageStatus.RECEIVED.name());
                        executor.execute(() -> process(id));
                    }
                });
            }
            return inserted;
        });
        if (!Boolean.TRUE.equals(stored)) {
            LOG.info(
                    "Duplicate {} {} from {} ignored",
                    notification.messageType(),
                    header.messageId(),
                    header.senderBpn());
            return new Receipt.Duplicate(header.messageId());
        }
        LOG.info("Received {} {} from {}", notification.messageType(), header.messageId(), header.senderBpn());
        return new Receipt.Accepted(header.messageId());
    }

    /** Processes all received messages without valid lease (scheduler; recovery after restarts). */
    public int processPending() {
        Instant now = context.now();
        List<StoredMessage> claimed =
                context.store().claimInbound(null, properties.batchSize(), now, now.plus(properties.lease()));
        claimed.forEach(this::processClaimed);
        return claimed.size();
    }

    /** Processes one received message (no effect if it is processed elsewhere or already done). */
    public void process(UUID id) {
        Instant now = context.now();
        context.store().claimInbound(id, 1, now, now.plus(properties.lease())).forEach(this::processClaimed);
    }

    void processClaimed(StoredMessage message) {
        ExchangeRecord r = message.record();
        String previous = MDC.get(CorrelationIdFilter.MDC_KEY);
        MDC.put(
                CorrelationIdFilter.MDC_KEY,
                r.correlationId() == null ? r.messageId().toString() : r.correlationId());
        try {
            Notification n = codec.decode(message.envelope().getBytes(StandardCharsets.UTF_8));
            InboundRejection rejection = check(n, message.edcBpn());
            if (rejection != null) {
                reject(r, rejection, rejectionMessage(rejection, n, message.edcBpn()), StepName.VALIDATION);
                return;
            }
            step(r, StepName.VALIDATION, StepStatus.OK, Duration.ZERO, Map.of("payloadHash", n.payloadHash()));
            if (n.messageType().signed()) {
                ensurePartnerKey(r, n);
            }
            long start = System.nanoTime();
            InboundResult result = transaction.execute(status -> {
                InboundResult outcome = dispatch(n);
                context.store()
                        .updateStatus(
                                r.id(),
                                MessageStatus.PROCESSED,
                                null,
                                null,
                                null,
                                context.now(),
                                outcome.caseId(),
                                outcome.caseNumber());
                return outcome;
            });
            var details = new LinkedHashMap<String, Object>();
            details.put("caseId", String.valueOf(result.caseId()));
            details.put("applied", result.applied());
            step(r, result.caseId(), StepName.PROCESSING, StepStatus.OK, elapsed(start), details);
            context.live().exchangeChanged(result.caseId(), r.messageId(), MessageStatus.PROCESSED.name());
            afterProcessing(n, result);
            LOG.info(
                    "Processed {} {} from {} (case {}, applied {})",
                    r.messageType(),
                    r.messageId(),
                    r.partnerBpnl(),
                    result.caseId(),
                    result.applied());
        } catch (PayloadValidationException ex) {
            reject(r, InboundRejection.INVALID, ex.getMessage() + " " + ex.violations(), StepName.VALIDATION);
        } catch (PpapException ex) {
            reject(
                    r,
                    InboundRejection.fromErrorCode(ex.code()),
                    ex.code() + ": " + ex.getMessage(),
                    StepName.PROCESSING);
        } catch (RuntimeException ex) {
            LOG.warn("Processing of {} failed", r.messageId(), ex);
            reject(r, InboundRejection.PROCESSING_FAILED, ex.toString(), StepName.PROCESSING);
        } finally {
            if (previous == null) {
                MDC.remove(CorrelationIdFilter.MDC_KEY);
            } else {
                MDC.put(CorrelationIdFilter.MDC_KEY, previous);
            }
        }
    }

    /** Origin and integrity checks; {@code null} if the message may be processed. */
    InboundRejection check(Notification n, String edcBpn) {
        String sender = n.header().senderBpn();
        if (!context.tenantBpnl().equals(n.header().receiverBpn())) {
            return InboundRejection.REJECTED_WRONG_RECIPIENT;
        }
        // the provider data plane forwards the consumer BPN of the contract agreement (Edc-Bpn, Tractus-X EDC 0.13)
        if (edcBpn != null && !edcBpn.isBlank() && !edcBpn.equals(sender)) {
            return InboundRejection.REJECTED_SENDER_MISMATCH;
        }
        if (!partners.hasActiveRelation(sender)) {
            return InboundRejection.REJECTED_UNKNOWN_PARTNER;
        }
        if (!n.payloadHashValid()) {
            return InboundRejection.REJECTED_PAYLOAD_HASH_MISMATCH;
        }
        return null;
    }

    private void ensurePartnerKey(ExchangeRecord r, Notification n) {
        String kid = n.signature() == null ? null : verifier.keyId(n.signature());
        String sender = n.header().senderBpn();
        if (kid == null || partnerKeys.find(sender, kid).isPresent()) {
            return;
        }
        long start = System.nanoTime();
        try {
            int stored = partnerKeys.fetch(sender, (step, status, duration, details) -> {
                if (status == StepStatus.FAILED) {
                    step(r, StepName.KEY_FETCH, status, duration, withStep(details, step));
                }
            });
            boolean found = partnerKeys.find(sender, kid).isPresent();
            step(
                    r,
                    StepName.KEY_FETCH,
                    found ? StepStatus.OK : StepStatus.FAILED,
                    elapsed(start),
                    Map.of("kid", kid, "keys", stored, "found", found));
        } catch (DataspaceException ex) {
            // signature becomes UNVERIFIED; the message is still processed (docs/api/exchange-api.md)
            step(
                    r,
                    StepName.KEY_FETCH,
                    StepStatus.FAILED,
                    elapsed(start),
                    Map.of("kid", kid, "error", ex.getMessage()));
        }
    }

    /** Phase 5: start the pull of an announced submission and the transfer of partner documents. */
    private void afterProcessing(Notification n, InboundResult result) {
        try {
            switch (n.messageType()) {
                case PPAP_SUBMISSION_AVAILABLE -> pull.jobs().nudge();
                case PPAP_SUBMISSION, PPAP_REQUEST -> {
                    if (result.caseId() != null
                            && pull.documents().scheduleForCase(result.caseId(), n.correlationId()) > 0) {
                        pull.jobs().nudge();
                    }
                }
                default -> {
                    // no follow-up
                }
            }
        } catch (RuntimeException ex) {
            LOG.warn(
                    "Follow-up of {} {} failed: {}", n.messageType(), n.header().messageId(), ex.toString());
        }
    }

    /**
     * {@code PPAP_SUBMISSION_AVAILABLE}: the own case must exist with the sender as partner; the pull job is queued
     * in the same transaction (idempotent per exchange id and revision).
     */
    private InboundResult submissionAvailable(Notification n) {
        SubmissionAvailableMessage available =
                pull.codec().decodeSubmissionAvailable(n.payload()).message();
        String sender = n.header().senderBpn();
        PartnerSharingUseCase.CaseRef own = pull.sharing()
                .findCase(available.exchangeId(), "CUSTOMER")
                .orElseThrow(() -> new NotFoundException("Approval case with exchange id", available.exchangeId()));
        if (!own.partnerBpnl().equals(sender)) {
            throw new PpapException(
                    PpapException.Kind.UNPROCESSABLE,
                    "UNKNOWN_SENDER",
                    "Sender " + sender + " is not the partner of the case");
        }
        boolean queued = pull.submissions()
                .schedule(
                        sender,
                        available.exchangeId(),
                        available.revision(),
                        available.payloadHash(),
                        SubmissionPullService.Trigger.NOTIFICATION,
                        n.correlationId(),
                        List.of());
        return new InboundResult(own.caseId(), queued, own.caseNumber());
    }

    private InboundResult dispatch(Notification n) {
        var message = new InboundMessage(n.header().senderBpn(), n.payload(), n.signature());
        return switch (n.messageType()) {
            case PPAP_SUBMISSION_AVAILABLE -> submissionAvailable(n);
            case PPAP_REQUEST -> requests.receiveRequest(message);
            case PPAP_REQUEST_WITHDRAWN -> withdrawals.receiveWithdrawal(message);
            case PPAP_STATUS_UPDATE -> statusUpdates.receiveStatusUpdate(message);
            case PPAP_SUBMISSION -> submissions.receiveSubmission(message);
            case PPAP_DECISION -> decisions.receiveDecision(message);
        };
    }

    private void reject(ExchangeRecord r, InboundRejection rejection, String error, StepName step) {
        UUID caseId = r.caseId();
        step(r, step, StepStatus.FAILED, Duration.ZERO, Map.of("error", rejection.name() + ": " + abbreviate(error)));
        context.store()
                .updateStatus(
                        r.id(),
                        MessageStatus.REJECTED,
                        null,
                        rejection.name(),
                        abbreviate(error),
                        context.now(),
                        caseId,
                        null);
        context.live().exchangeChanged(caseId, r.messageId(), MessageStatus.REJECTED.name());
        LOG.warn("Rejected {} {} from {}: {} {}", r.messageType(), r.messageId(), r.partnerBpnl(), rejection, error);
    }

    private void storeInvalid(
            byte[] body, String edcBpn, String agreementId, PayloadValidationException ex, Instant now) {
        UUID id = Ids.newId();
        String sender = edcBpn == null || edcBpn.isBlank() ? UNKNOWN : edcBpn;
        String text = new String(body, StandardCharsets.UTF_8);
        transaction.executeWithoutResult(status -> {
            context.store()
                    .insertInbound(
                            new NewMessage(
                                    id,
                                    UUID.randomUUID(),
                                    Direction.INBOUND,
                                    UNKNOWN,
                                    sender.length() > 16 ? UNKNOWN : sender,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    text.length() > 100_000 ? text.substring(0, 100_000) : text,
                                    edcBpn,
                                    now),
                            MessageStatus.REJECTED,
                            InboundRejection.INVALID.name(),
                            abbreviate(String.valueOf(ex.violations())));
            var details = new LinkedHashMap<String, Object>();
            details.put("size", body.length);
            put(details, "agreementId", agreementId);
            context.store()
                    .addStep(
                            id,
                            new com.msg.ppap.exchange.domain.ExchangeStep(
                                    1, StepName.RECEIPT, StepStatus.OK, now, 0, details));
            context.store()
                    .addStep(
                            id,
                            new com.msg.ppap.exchange.domain.ExchangeStep(
                                    1,
                                    StepName.VALIDATION,
                                    StepStatus.FAILED,
                                    now,
                                    0,
                                    Map.of("error", abbreviate(String.valueOf(ex.violations())))));
        });
        LOG.warn("Rejected invalid notification (sender {}): {}", sender, ex.violations());
    }

    private static String rejectionMessage(InboundRejection rejection, Notification n, String edcBpn) {
        return switch (rejection) {
            case REJECTED_WRONG_RECIPIENT -> "addressed to " + n.header().receiverBpn();
            case REJECTED_SENDER_MISMATCH ->
                "header sender " + n.header().senderBpn() + " but data plane consumer " + edcBpn;
            case REJECTED_UNKNOWN_PARTNER ->
                "no active relation to " + n.header().senderBpn();
            case REJECTED_PAYLOAD_HASH_MISMATCH -> "payload hash does not match " + n.payloadHash();
            default -> rejection.name();
        };
    }

    private void step(ExchangeRecord r, StepName step, StepStatus status, Duration duration, Map<String, Object> d) {
        step(r, r.caseId(), step, status, duration, d);
    }

    private void step(
            ExchangeRecord r, UUID caseId, StepName step, StepStatus status, Duration duration, Map<String, Object> d) {
        StepTarget target = step == StepName.KEY_FETCH ? StepTarget.PUBLIC_KEYS : StepTarget.NOTIFICATION;
        context.step(
                r.id(),
                caseId,
                r.messageId(),
                r.status(),
                Math.max(1, r.attempts()),
                step,
                status,
                duration,
                target.apply(d, null));
    }

    private static Map<String, Object> withStep(Map<String, Object> details, StepName step) {
        var copy = new LinkedHashMap<>(details);
        copy.put("edcStep", step.name());
        return copy;
    }

    private static Duration elapsed(long startNanos) {
        return Duration.ofNanos(System.nanoTime() - startNanos);
    }

    private static void put(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    private static String abbreviate(String text) {
        return text == null || text.length() <= 2000 ? text : text.substring(0, 2000) + "…";
    }
}
