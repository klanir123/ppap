package com.msg.ppap.exchange.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Exponential backoff of outbound deliveries: {@code initial · 2^(attempt-1)}, capped at {@code max}; after {@code
 * maxAttempts} failed attempts the message is given up ({@code FAILED}, manual retry).
 */
public record RetryPolicy(Duration initial, Duration max, int maxAttempts) {

    public RetryPolicy {
        Objects.requireNonNull(initial, "initial");
        Objects.requireNonNull(max, "max");
        if (initial.isNegative() || initial.isZero() || max.compareTo(initial) < 0 || maxAttempts < 1) {
            throw new IllegalArgumentException("invalid retry policy");
        }
    }

    /** Delay after the given number of failed attempts (≥ 1). */
    public Duration delayAfter(int failedAttempts) {
        int exponent = Math.max(0, Math.min(failedAttempts - 1, 30));
        Duration delay = initial.multipliedBy(1L << exponent);
        return delay.compareTo(max) > 0 ? max : delay;
    }

    /**
     * Time of the next attempt.
     *
     * @return empty if all attempts are used up
     */
    public Optional<Instant> nextAttempt(int failedAttempts, Instant now) {
        if (failedAttempts >= maxAttempts) {
            return Optional.empty();
        }
        return Optional.of(now.plus(delayAfter(failedAttempts)));
    }
}
