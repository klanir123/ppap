package com.msg.ppap.exchange.domain;

/**
 * Status of an exchanged message. Outbound: {@code PENDING → IN_PROGRESS → DELIVERED | PENDING (retry) | FAILED}.
 * Inbound: {@code RECEIVED → PROCESSED | REJECTED}.
 */
public enum MessageStatus {
    PENDING,
    IN_PROGRESS,
    DELIVERED,
    FAILED,
    RECEIVED,
    PROCESSED,
    REJECTED;

    /** Whether no further automatic processing happens. */
    public boolean terminal() {
        return this == DELIVERED || this == FAILED || this == PROCESSED || this == REJECTED;
    }
}
