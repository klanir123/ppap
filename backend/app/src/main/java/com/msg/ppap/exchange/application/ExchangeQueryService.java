package com.msg.ppap.exchange.application;

import com.msg.ppap.exchange.application.port.out.DataspaceConnector;
import com.msg.ppap.exchange.application.port.out.ExchangeMessageStore.StoredMessage;
import com.msg.ppap.exchange.domain.Direction;
import com.msg.ppap.exchange.domain.ExchangeRecord;
import com.msg.ppap.exchange.domain.MessageStatus;
import com.msg.ppap.platform.domain.NotFoundException;
import com.msg.ppap.platform.domain.PpapException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/** Queries and manual actions of the exchange cockpit (docs/api/exchange-api.md, REST). */
@Service
public class ExchangeQueryService {

    public static final String NOT_RETRYABLE = "MESSAGE_NOT_RETRYABLE";

    private final ExchangeContext context;
    private final DataspaceConnector connector;
    private final ProvisioningService provisioning;
    private final OutboundDeliveryService delivery;
    private final InboxService inbox;
    private final PullJobs pulls;

    public ExchangeQueryService(
            ExchangeContext context,
            DataspaceConnector connector,
            ProvisioningService provisioning,
            OutboundDeliveryService delivery,
            InboxService inbox,
            PullJobs pulls) {
        this.pulls = pulls;
        this.context = context;
        this.connector = connector;
        this.provisioning = provisioning;
        this.delivery = delivery;
        this.inbox = inbox;
    }

    /** Exchange records of a case (newest first). */
    public List<ExchangeRecord> byCase(UUID caseId) {
        return context.store().findByCase(caseId);
    }

    public Page<ExchangeRecord> search(Direction direction, MessageStatus status, String partnerBpnl, Pageable page) {
        return search(direction, status, partnerBpnl, List.of(), page);
    }

    /** Search with message/record types (empty = all). */
    public Page<ExchangeRecord> search(
            Direction direction,
            MessageStatus status,
            String partnerBpnl,
            java.util.Collection<String> messageTypes,
            Pageable page) {
        return context.store().search(direction, status, partnerBpnl, messageTypes, page);
    }

    /** Record with envelope (diagnosis). */
    public StoredMessage detail(UUID id) {
        return context.store().find(id).orElseThrow(() -> new NotFoundException("Exchange message", id));
    }

    /**
     * Manual retry: failed deliveries are queued again (attempts reset), rejected inbound messages are processed
     * again.
     */
    public ExchangeRecord retry(UUID id) {
        ExchangeRecord record = detail(id).record();
        if (!record.retryable() || !context.store().resetForRetry(id, context.now())) {
            throw new RetryNotPossible(record);
        }
        if (record.direction() == Direction.OUTBOUND) {
            delivery.nudge();
        } else if (record.pull()) {
            pulls.nudge();
        } else {
            inbox.process(id);
        }
        context.live().exchangeChanged(record.caseId(), record.messageId(), "RETRY");
        return context.store().findRecord(id).orElseThrow();
    }

    public ExchangeStatus status() {
        Map<String, Long> counts = context.store().countByDirectionAndStatus();
        return new ExchangeStatus(
                connector.health(),
                provisioning.state(),
                counts.getOrDefault(key(Direction.OUTBOUND, MessageStatus.PENDING), 0L)
                        + counts.getOrDefault(key(Direction.OUTBOUND, MessageStatus.IN_PROGRESS), 0L),
                counts.getOrDefault(key(Direction.OUTBOUND, MessageStatus.FAILED), 0L),
                counts.getOrDefault(key(Direction.INBOUND, MessageStatus.REJECTED), 0L));
    }

    /** Key of {@link com.msg.ppap.exchange.application.port.out.ExchangeMessageStore#countByDirectionAndStatus()}. */
    public static String key(Direction direction, MessageStatus status) {
        return direction.name() + "/" + status.name();
    }

    /** The message is not in a state that allows a manual retry (409). */
    static final class RetryNotPossible extends PpapException {

        RetryNotPossible(ExchangeRecord record) {
            super(
                    Kind.CONFLICT,
                    NOT_RETRYABLE,
                    "Message " + record.messageId() + " (" + record.direction() + ", " + record.status()
                            + ") cannot be retried");
        }
    }
}
