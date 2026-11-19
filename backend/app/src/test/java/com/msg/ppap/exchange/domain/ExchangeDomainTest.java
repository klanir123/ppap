package com.msg.ppap.exchange.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExchangeDomainTest {

    final RetryPolicy policy = new RetryPolicy(Duration.ofSeconds(1), Duration.ofMinutes(15), 20);

    @Test
    void backoffDoublesUpToTheMaximum() {
        assertThat(policy.delayAfter(1)).isEqualTo(Duration.ofSeconds(1));
        assertThat(policy.delayAfter(2)).isEqualTo(Duration.ofSeconds(2));
        assertThat(policy.delayAfter(5)).isEqualTo(Duration.ofSeconds(16));
        assertThat(policy.delayAfter(10)).isEqualTo(Duration.ofSeconds(512));
        assertThat(policy.delayAfter(11)).isEqualTo(Duration.ofMinutes(15));
        assertThat(policy.delayAfter(19)).isEqualTo(Duration.ofMinutes(15));
        assertThat(policy.delayAfter(1000)).isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    void givesUpAfterMaxAttempts() {
        Instant now = Instant.parse("2026-11-19T10:00:00Z");
        assertThat(policy.nextAttempt(1, now)).contains(now.plusSeconds(1));
        assertThat(policy.nextAttempt(19, now)).contains(now.plus(Duration.ofMinutes(15)));
        assertThat(policy.nextAttempt(20, now)).isEmpty();
    }

    @Test
    void rejectsInvalidPolicies() {
        assertThatThrownBy(() -> new RetryPolicy(Duration.ZERO, Duration.ofMinutes(1), 3))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RetryPolicy(Duration.ofMinutes(2), Duration.ofMinutes(1), 3))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RetryPolicy(Duration.ofSeconds(1), Duration.ofMinutes(1), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void mapsErrorCodesToRejections() {
        assertThat(InboundRejection.fromErrorCode("INVALID_STATE_TRANSITION"))
                .isEqualTo(InboundRejection.REJECTED_INVALID_STATE);
        assertThat(InboundRejection.fromErrorCode("REVISION_CONFLICT"))
                .isEqualTo(InboundRejection.REJECTED_INVALID_STATE);
        assertThat(InboundRejection.fromErrorCode("NOT_FOUND")).isEqualTo(InboundRejection.REJECTED_INVALID_STATE);
        assertThat(InboundRejection.fromErrorCode("SIGNATURE_INVALID"))
                .isEqualTo(InboundRejection.REJECTED_SIGNATURE_INVALID);
        assertThat(InboundRejection.fromErrorCode("UNKNOWN_SENDER"))
                .isEqualTo(InboundRejection.REJECTED_SENDER_MISMATCH);
        assertThat(InboundRejection.fromErrorCode("PAYLOAD_SCHEMA_VIOLATION")).isEqualTo(InboundRejection.INVALID);
        assertThat(InboundRejection.fromErrorCode("SOMETHING")).isEqualTo(InboundRejection.PROCESSING_FAILED);
        assertThat(InboundRejection.fromErrorCode(null)).isEqualTo(InboundRejection.PROCESSING_FAILED);
    }

    @Test
    void recordRetryabilityAndStatus() {
        var failed = record(Direction.OUTBOUND, MessageStatus.FAILED);
        assertThat(failed.retryable()).isTrue();
        assertThat(record(Direction.OUTBOUND, MessageStatus.DELIVERED).retryable())
                .isFalse();
        assertThat(record(Direction.INBOUND, MessageStatus.REJECTED).retryable())
                .isTrue();
        assertThat(record(Direction.INBOUND, MessageStatus.PROCESSED).retryable())
                .isFalse();
        var step = new ExchangeStep(1, StepName.DELIVERY, StepStatus.OK, Instant.now(), 5, Map.of("httpStatus", 202));
        assertThat(failed.withSteps(List.of(step)).steps()).containsExactly(step);
        assertThat(MessageStatus.DELIVERED.terminal()).isTrue();
        assertThat(MessageStatus.PENDING.terminal()).isFalse();
        assertThat(ProvisioningState.INITIAL.ok()).isFalse();
    }

    static ExchangeRecord record(Direction direction, MessageStatus status) {
        return new ExchangeRecord(
                UUID.randomUUID(),
                UUID.randomUUID(),
                direction,
                "PPAP_REQUEST",
                "BPNL000000TIER01",
                null,
                null,
                null,
                null,
                status,
                1,
                null,
                null,
                null,
                Instant.now(),
                null,
                null,
                null,
                null);
    }
}
