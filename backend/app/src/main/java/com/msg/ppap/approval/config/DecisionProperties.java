package com.msg.ppap.approval.config;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Decision settings (UC-07).
 *
 * @param allowTechnicalClients technical clients ({@code SYSTEM_INTEGRATION}) may record findings and decide
 *     ({@code PPAP_DECISION_ALLOW_TECHNICAL_CLIENTS}, default {@code false})
 * @param maxAuthAgeSeconds if set, a decision requires an interactive authentication ({@code auth_time}) not older
 *     than this ({@code PPAP_DECISION_MAX_AUTH_AGE_SECONDS}); otherwise {@code 401 RECENT_AUTH_REQUIRED}
 */
@ConfigurationProperties("ppap.decision")
public record DecisionProperties(
        boolean allowTechnicalClients, @Nullable Long maxAuthAgeSeconds) {}
