package com.msg.ppap.usage.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A counted usage, unique per (exchange id, revision, metric) – idempotency key of the event-based counting.
 *
 * @param month {@code YYYY-MM} (UTC) of the submission
 */
public record UsageEvent(
        UUID exchangeId, int revision, UsageMetric metric, String month, UUID caseId, Instant occurredAt) {

    public UsageEvent {
        Objects.requireNonNull(exchangeId, "exchangeId");
        Objects.requireNonNull(metric, "metric");
        Objects.requireNonNull(month, "month");
        Objects.requireNonNull(caseId, "caseId");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}
