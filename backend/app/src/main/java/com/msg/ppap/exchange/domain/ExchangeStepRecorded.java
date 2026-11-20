package com.msg.ppap.exchange.domain;

import java.util.UUID;

/**
 * A step of an exchange record was persisted (source of the live log {@code connector.log}, UC-10).
 *
 * @param recordId id of the exchange record
 * @param messageId message id of the envelope
 * @param messageType message / record type
 * @param correlationId correlation id of the record
 * @param caseId own case, if known
 * @param direction direction of the record
 * @param partnerBpnl partner of the record
 * @param step the step
 */
public record ExchangeStepRecorded(
        UUID recordId,
        UUID messageId,
        String messageType,
        String correlationId,
        UUID caseId,
        Direction direction,
        String partnerBpnl,
        ExchangeStep step) {}
