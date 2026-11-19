package com.msg.ppap.exchange.adapter.in.events;

import com.msg.ppap.approval.ApprovalEvent;
import com.msg.ppap.approval.SubmissionSubmitted;
import com.msg.ppap.approval.application.port.in.OutboundMessageUseCase;
import com.msg.ppap.exchange.application.LiveEventService;
import com.msg.ppap.exchange.application.OutboxService;
import com.msg.ppap.exchange.domain.RecordTypes;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Bridge from the approval module to the exchange.
 *
 * <p><b>Transactional outbox:</b> the outbox entry is written by a {@code BEFORE_COMMIT} listener, i.e. in the same
 * transaction as the case change – either both are committed or neither. A failure (e.g. a payload violating the
 * schema) rolls back the user action instead of silently losing the message. Alternatives considered: an explicit
 * port call from the approval services would make {@code approval} depend on {@code exchange} (cycle, the module
 * would have to know the dataspace); an {@code AFTER_COMMIT} listener or the Modulith event publication registry
 * would not be atomic with the change (window between commit and outbox write) and run asynchronously in another
 * transaction.
 */
@Component
class ApprovalEventsListener {

    private final OutboundMessageUseCase outbound;
    private final OutboxService outbox;
    private final LiveEventService live;

    ApprovalEventsListener(OutboundMessageUseCase outbound, OutboxService outbox, LiveEventService live) {
        this.outbound = outbound;
        this.outbox = outbox;
        this.live = live;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    void enqueue(ApprovalEvent event) {
        // phase 5: the twin is registered (local outbox task) before the notification of the same exchange id
        if (event instanceof SubmissionSubmitted s && registersTwin(s.exchangeMode())) {
            outbox.enqueueTask(
                    RecordTypes.TWIN_REGISTRATION,
                    s.caseId(),
                    s.caseNumber(),
                    s.exchangeId(),
                    s.partnerBpnl(),
                    "{\"exchangeId\":\"" + s.exchangeId() + "\",\"revision\":" + s.revision() + ",\"exchangeMode\":\""
                            + s.exchangeMode() + "\"}");
        }
        outbound.messageFor(event).ifPresent(outbox::enqueue);
    }

    static boolean registersTwin(String exchangeMode) {
        return "PULL".equals(exchangeMode) || "PUSH_NOTIFY_PULL_PAYLOAD".equals(exchangeMode);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void notifyLive(ApprovalEvent event) {
        live.caseChanged(event.caseId(), event.toStatus());
    }
}
