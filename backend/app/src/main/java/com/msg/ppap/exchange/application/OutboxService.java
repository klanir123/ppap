package com.msg.ppap.exchange.application;

import com.msg.ppap.approval.application.port.in.OutboundMessage;
import com.msg.ppap.exchange.application.port.out.ExchangeMessageStore.NewMessage;
import com.msg.ppap.exchange.domain.Direction;
import com.msg.ppap.masterdata.api.MasterdataQueries;
import com.msg.ppap.platform.domain.Ids;
import com.msg.ppap.semantics.api.Notification;
import com.msg.ppap.semantics.api.NotificationCodec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Transactional outbox: writes the outbound message – the complete, schema-validated {@code PpapNotification}
 * envelope – in the transaction of the case change. The delivery worker picks it up after commit.
 */
@Service
public class OutboxService {

    private static final Logger LOG = LoggerFactory.getLogger(OutboxService.class);

    private final ExchangeContext context;
    private final NotificationCodec codec;
    private final MasterdataQueries masterdata;
    private final OutboundDeliveryService delivery;

    public OutboxService(
            ExchangeContext context,
            NotificationCodec codec,
            MasterdataQueries masterdata,
            OutboundDeliveryService delivery) {
        this.context = context;
        this.codec = codec;
        this.masterdata = masterdata;
        this.delivery = delivery;
    }

    /**
     * Enqueues the message; must run inside the transaction of the change (fails otherwise).
     *
     * @return id of the outbox entry
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID enqueue(OutboundMessage message) {
        Instant now = context.now();
        UUID messageId = UUID.randomUUID();
        String correlationId = ExchangeContext.correlationId();
        var notification = new Notification(
                new Notification.Header(
                        messageId,
                        message.payload().modelUrn(),
                        now,
                        context.tenantBpnl(),
                        message.partnerBpnl(),
                        null,
                        null,
                        Notification.HEADER_VERSION),
                message.messageType(),
                message.exchangeId(),
                correlationId,
                message.payload().canonical(),
                message.payload().hash(),
                message.signature());
        String envelope = new String(codec.encode(notification), StandardCharsets.UTF_8);
        String partnerName = masterdata
                .findPartner(message.partnerBpnl())
                .map(MasterdataQueries.PartnerInfo::name)
                .orElse(null);
        UUID id = Ids.newId();
        context.store()
                .insertOutbound(
                        new NewMessage(
                                id,
                                messageId,
                                Direction.OUTBOUND,
                                message.messageType().name(),
                                message.partnerBpnl(),
                                partnerName,
                                message.caseId(),
                                message.caseNumber(),
                                message.exchangeId(),
                                message.payload().hash(),
                                correlationId,
                                envelope,
                                null,
                                now),
                        now);
        LOG.info(
                "Outbox: {} {} for case {} to {} queued",
                message.messageType(),
                messageId,
                message.caseId(),
                message.partnerBpnl());
        UUID caseId = message.caseId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                context.live().exchangeChanged(caseId, messageId, "PENDING");
                delivery.nudge();
            }
        });
        return id;
    }
}
