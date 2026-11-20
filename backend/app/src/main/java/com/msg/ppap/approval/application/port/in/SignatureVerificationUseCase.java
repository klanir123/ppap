package com.msg.ppap.approval.application.port.in;

/**
 * Re-verification of {@code UNVERIFIED} signatures of received revisions once the partner's keys are known (called by
 * the exchange module after every successful key fetch, docs/api/cascade-transparency-api.md §7).
 */
public interface SignatureVerificationUseCase {

    /**
     * Verifies all {@code UNVERIFIED} revisions received from the partner again.
     *
     * @return number of revisions whose status changed
     */
    int reverifyUnverified(String partnerBpnl);
}
