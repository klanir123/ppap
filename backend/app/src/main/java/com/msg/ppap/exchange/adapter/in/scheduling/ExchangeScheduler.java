package com.msg.ppap.exchange.adapter.in.scheduling;

import com.msg.ppap.exchange.application.InboxService;
import com.msg.ppap.exchange.application.LiveEventService;
import com.msg.ppap.exchange.application.OutboundDeliveryService;
import com.msg.ppap.exchange.application.ProvisioningService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodic jobs of the exchange: outbox worker, inbox recovery, provisioning (retry until the EDC is up, re-run
 * after changes and periodically) and the heartbeat of the live event stream. Disabled with {@code
 * ppap.exchange.scheduling-enabled=false} (tests).
 */
@Component
@ConditionalOnProperty(name = "ppap.exchange.scheduling-enabled", havingValue = "true", matchIfMissing = true)
class ExchangeScheduler {

    private final OutboundDeliveryService delivery;
    private final InboxService inbox;
    private final ProvisioningService provisioning;
    private final LiveEventService live;

    ExchangeScheduler(
            OutboundDeliveryService delivery,
            InboxService inbox,
            ProvisioningService provisioning,
            LiveEventService live) {
        this.delivery = delivery;
        this.inbox = inbox;
        this.provisioning = provisioning;
        this.live = live;
    }

    @Scheduled(fixedDelayString = "${ppap.exchange.worker-interval:2s}", initialDelayString = "5s")
    void deliverOutbox() {
        delivery.deliverDue();
    }

    @Scheduled(fixedDelayString = "${ppap.exchange.worker-interval:2s}", initialDelayString = "5s")
    void processInbox() {
        inbox.processPending();
    }

    @Scheduled(fixedDelayString = "${ppap.exchange.worker-interval:2s}", initialDelayString = "3s")
    void provision() {
        provisioning.reconcileIfDue();
    }

    @Scheduled(
            fixedDelayString = "${ppap.exchange.heartbeat:25s}",
            initialDelayString = "${ppap.exchange.heartbeat:25s}")
    void heartbeat() {
        live.heartbeat();
    }
}
