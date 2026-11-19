package com.msg.ppap.semantics.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Withdrawal of a sent request by the customer, exchange model {@code PpapRequestWithdrawn}.
 *
 * @param exchangeId exchange id of the case
 * @param reason cancellation reason
 * @param withdrawnAt time of the cancellation
 * @param withdrawnBy cancelling user
 */
public record WithdrawalMessage(UUID exchangeId, String reason, Instant withdrawnAt, String withdrawnBy) {}
