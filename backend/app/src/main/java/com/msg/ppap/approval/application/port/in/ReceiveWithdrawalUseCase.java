package com.msg.ppap.approval.application.port.in;

/** Supplier side: the customer withdrew a sent request ({@code PpapRequestWithdrawn}); the case is cancelled. */
public interface ReceiveWithdrawalUseCase {

    InboundResult receiveWithdrawal(InboundMessage message);
}
