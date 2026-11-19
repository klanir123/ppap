package com.msg.ppap.exchange.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jmolecules.ddd.annotation.AggregateRoot;
import org.jmolecules.ddd.annotation.Identity;

/**
 * An exchanged message with its processing state and steps – outbox entry (outbound) or inbox entry (inbound) and
 * at the same time the visible exchange record of the cockpit (docs/api/exchange-api.md, principle 4).
 *
 * @param messageId message id of the envelope ({@code header.messageId})
 * @param messageType message type, e.g. {@code PPAP_SUBMISSION}
 * @param partnerBpnl receiver (outbound) or sender (inbound)
 * @param caseId own case, if known
 * @param caseNumber own case number, if known
 * @param exchangeId exchange id of the case pair
 * @param attempts delivery attempts (outbound) or processing runs (inbound)
 * @param nextAttemptAt next delivery attempt of a pending outbound message
 * @param errorCode machine-readable error/rejection code, e.g. {@code REJECTED_UNKNOWN_PARTNER}
 */
@AggregateRoot
public record ExchangeRecord(
        @Identity UUID id,
        UUID messageId,
        Direction direction,
        String messageType,
        String partnerBpnl,
        String partnerName,
        UUID caseId,
        String caseNumber,
        UUID exchangeId,
        MessageStatus status,
        int attempts,
        Instant nextAttemptAt,
        String payloadHash,
        String correlationId,
        Instant createdAt,
        Instant completedAt,
        String errorCode,
        String lastError,
        List<ExchangeStep> steps) {

    public ExchangeRecord {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(status, "status");
        steps = steps == null ? List.of() : List.copyOf(steps);
    }

    /** Copy with steps. */
    public ExchangeRecord withSteps(List<ExchangeStep> newSteps) {
        return new ExchangeRecord(
                id,
                messageId,
                direction,
                messageType,
                partnerBpnl,
                partnerName,
                caseId,
                caseNumber,
                exchangeId,
                status,
                attempts,
                nextAttemptAt,
                payloadHash,
                correlationId,
                createdAt,
                completedAt,
                errorCode,
                lastError,
                newSteps);
    }

    /** Whether a manual retry is possible (failed delivery or rejected/failed processing). */
    public boolean retryable() {
        return (direction == Direction.OUTBOUND && status == MessageStatus.FAILED)
                || (direction == Direction.INBOUND && status == MessageStatus.REJECTED);
    }
}
