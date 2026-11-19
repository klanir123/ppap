package com.msg.ppap.exchange.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings of the transactional outbox, the inbox and the provisioning.
 *
 * @param workerInterval polling interval of the outbox/inbox worker ({@code PPAP_EXCHANGE_WORKER_INTERVAL})
 * @param maxAttempts delivery attempts before a message becomes {@code FAILED} ({@code PPAP_EXCHANGE_MAX_ATTEMPTS})
 * @param initialBackoff delay after the first failed attempt (doubled per attempt)
 * @param maxBackoff upper bound of the delay
 * @param lease how long a claimed message is reserved for one worker
 * @param batchSize messages claimed per worker run
 * @param provisioningRetry retry interval of the provisioning while the EDC is unreachable
 * @param provisioningInterval periodic re-provisioning (EDC restarts, lost state)
 * @param heartbeat heartbeat interval of the live event stream
 */
@ConfigurationProperties("ppap.exchange")
public record ExchangeProperties(
        @DefaultValue("2s") Duration workerInterval,
        @DefaultValue("20") int maxAttempts,
        @DefaultValue("1s") Duration initialBackoff,
        @DefaultValue("15m") Duration maxBackoff,
        @DefaultValue("2m") Duration lease,
        @DefaultValue("10") int batchSize,
        @DefaultValue("15s") Duration provisioningRetry,
        @DefaultValue("10m") Duration provisioningInterval,
        @DefaultValue("25s") Duration heartbeat) {}
