package com.msg.ppap.exchange.adapter.in.events;

import com.msg.ppap.approval.application.port.in.SignatureVerificationUseCase;
import com.msg.ppap.exchange.domain.PartnerKeysFetched;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * After every successful key fetch, revisions of the partner whose signature was {@code UNVERIFIED} are verified
 * again (docs/api/cascade-transparency-api.md §7). Runs asynchronously after the commit of the fetching transaction
 * so that it never competes with the processing of the message that triggered the fetch.
 */
@Component
class PartnerKeysListener {

    private static final Logger LOG = LoggerFactory.getLogger(PartnerKeysListener.class);

    private final SignatureVerificationUseCase verification;

    PartnerKeysListener(SignatureVerificationUseCase verification) {
        this.verification = verification;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void on(PartnerKeysFetched event) {
        try {
            int changed = verification.reverifyUnverified(event.partnerBpnl());
            if (changed > 0) {
                LOG.info("Re-verified {} revision(s) of partner {} after key fetch", changed, event.partnerBpnl());
            }
        } catch (RuntimeException ex) {
            LOG.warn("Re-verification of partner {} failed: {}", event.partnerBpnl(), ex.toString());
        }
    }
}
