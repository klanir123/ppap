package com.msg.ppap.exchange.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Customer-side pull (docs/api/pull-api.md, mode PULL) and document transfer.
 *
 * @param pollInterval polling of the suppliers' DTRs for open cases ({@code PPAP_PULL_POLL_INTERVAL})
 * @param pollEnabled whether the poller runs (the pull of announced submissions and documents always runs)
 * @param offerCacheTtl how long a found DTR offer of a partner is cached
 * @param lookahead how many revisions after the last received one the poller checks
 * @param noOfferCacheTtl how long a missing DTR offer is cached (partner may still be provisioning; {@code
 *     PPAP_PULL_NO_OFFER_CACHE_TTL})
 */
@ConfigurationProperties("ppap.pull")
public record PullProperties(
        @DefaultValue("60s") Duration pollInterval,
        @DefaultValue("true") boolean pollEnabled,
        @DefaultValue("10m") Duration offerCacheTtl,
        @DefaultValue("3") int lookahead,
        @DefaultValue("60s") Duration noOfferCacheTtl) {}
