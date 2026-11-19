package com.msg.ppap.approval.application.port.in;

/**
 * Supplier side: a signed {@code PpapDecision} of the customer arrives (UC-08). The decision must refer to the hash
 * of the submitted revision; the case takes the decided status. Idempotent by payload hash.
 */
public interface ReceiveDecisionUseCase {

    InboundResult receiveDecision(InboundMessage message);
}
