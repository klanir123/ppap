package com.msg.ppap.exchange.config;

import java.net.URI;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Connection settings of the participant's own Tractus-X EDC.
 *
 * @param managementUrl base URL of the control plane management API, e.g. {@code http://edc-cp:8081/management}
 *     ({@code PPAP_EDC_MANAGEMENT_URL})
 * @param apiKey management API key sent as {@code X-Api-Key} ({@code PPAP_EDC_API_KEY})
 * @param dspUrl public DSP protocol URL of this connector ({@code PPAP_EDC_DSP_URL})
 * @param participantId EDC participant id, i.e. the BPNL ({@code PPAP_EDC_PARTICIPANT_ID})
 */
@ConfigurationProperties("ppap.edc")
public record EdcProperties(
        URI managementUrl,
        @Nullable String apiKey,
        @Nullable URI dspUrl,
        @Nullable String participantId) {}
