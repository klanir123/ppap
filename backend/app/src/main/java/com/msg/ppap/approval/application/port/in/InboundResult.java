package com.msg.ppap.approval.application.port.in;

import java.util.UUID;

/**
 * Result of processing an inbound message.
 *
 * @param caseId affected case
 * @param applied {@code false} if the message had already been processed (idempotent duplicate)
 */
public record InboundResult(UUID caseId, boolean applied) {}
