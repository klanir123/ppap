package com.msg.ppap.approval.application.port.in;

/**
 * Supplier side: a {@code PpapRequest} of a customer arrives (UC-04). Creates a case in status REQUESTED (source
 * {@code EXCHANGE}); idempotent by exchange id. Called by the exchange module from phase 4 on.
 */
public interface ReceiveRequestUseCase {

    InboundResult receiveRequest(InboundMessage message);
}
