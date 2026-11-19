package com.msg.ppap.exchange.adapter.in.scheduling;

import com.msg.ppap.exchange.application.InboxService;
import com.msg.ppap.exchange.application.LiveEventService;
import com.msg.ppap.exchange.application.OutboundDeliveryService;
import com.msg.ppap.exchange.application.ProvisioningService;
import com.msg.ppap.exchange.application.PullJobs;
import com.msg.ppap.exchange.application.PullPollingService;
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
    private final PullJobs pulls;
    private final PullPollingService poller;

    ExchangeScheduler(
            OutboundDeliveryService delivery,
            InboxService inbox,
            ProvisioningService provisioning,
            LiveEventService live,
            PullJobs pulls,
            PullPollingService poller) {
        this.pulls = pulls;
        this.poller = poller;
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

    /** Phase 5: pull jobs (announced/polled submissions, documents). */
    @Scheduled(fixedDelayString = "${ppap.exchange.worker-interval:2s}", initialDelayString = "6s")
    void runPulls() {
        pulls.runDue();
    }

    /** Phase 5, mode PULL: poll the suppliers' DTRs for the own open requests. */
    @Scheduled(
            fixedDelayString = "${ppap.pull.poll-interval:60s}",
            initialDelayString = "${ppap.pull.poll-interval:60s}")
    void pollDtrs() {
        // not before the own provisioning completed (start-up: partners may not have provisioned either)
        if (!provisioning.state().ok()) {
            return;
        }
        if (poller.poll() > 0) {
            pulls.nudge();
        }
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
