package com.msg.ppap.approval.application.port.in;

/**
 * A {@code PpapStatusUpdate} of the partner arrives: the customer's case follows the supplier to {@code IN_PROGRESS},
 * the supplier's case follows the customer to {@code IN_REVIEW}. Duplicates and overtaken updates have no effect.
 */
public interface ReceiveStatusUpdateUseCase {

    InboundResult receiveStatusUpdate(InboundMessage message);
}
