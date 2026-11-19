package com.msg.ppap.exchange.config;

import java.net.URI;
import java.time.Duration;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Connection settings of the participant's own Tractus-X EDC (docs/api/exchange-api.md, „Konfiguration“).
 *
 * @param managementUrl base URL of the control plane management API, e.g. {@code http://edc-cp:8081/management}
 *     ({@code PPAP_EDC_MANAGEMENT_URL})
 * @param apiKey management API key sent as {@code X-Api-Key} ({@code PPAP_EDC_API_KEY})
 * @param dspUrl public DSP protocol URL of this connector ({@code PPAP_EDC_DSP_URL})
 * @param participantId EDC participant id, i.e. the DID ({@code PPAP_EDC_PARTICIPANT_ID})
 * @param callbackBaseUrl base URL under which the own data plane reaches this backend ({@code
 *     PPAP_EDC_CALLBACK_BASE_URL}, e.g. {@code http://oem-app:8080})
 * @param callbackSecret shared secret the own data plane sends to {@code /internal/edc/**} ({@code
 *     PPAP_EDC_CALLBACK_SECRET}); without it the internal endpoints reject every request
 * @param callbackSecretHeader header carrying the shared secret (DataAddress {@code authKey})
 * @param callbackSecretName vault secret holding the shared secret (DataAddress {@code secretName})
 * @param requestTimeout timeout of management API and data plane calls
 * @param negotiationTimeout maximum time to wait for negotiation + transfer (EDR)
 * @param pollInterval polling interval while waiting for the EDR
 * @param policy policy settings of the offered assets
 * @param vault secret store of the participant (the same OpenBao/Vault the EDC data plane reads)
 */
@ConfigurationProperties("ppap.edc")
public record EdcProperties(
        URI managementUrl,
        @Nullable String apiKey,
        @Nullable URI dspUrl,
        @Nullable String participantId,
        @Nullable String callbackBaseUrl,
        @Nullable String callbackSecret,
        @DefaultValue("X-Ppap-Edc-Secret") String callbackSecretHeader,
        @DefaultValue("ppap-edc-callback-secret") String callbackSecretName,
        @DefaultValue("10s") Duration requestTimeout,
        @DefaultValue("60s") Duration negotiationTimeout,
        @DefaultValue("250ms") Duration pollInterval,
        @DefaultValue Policy policy,
        @DefaultValue Vault vault) {

    /** Whether the callback secret is configured. */
    public boolean callbackSecretConfigured() {
        return callbackSecret != null && !callbackSecret.isBlank();
    }

    /**
     * Policy settings (CX-0152, namespace 2025/9).
     *
     * @param usagePurpose UsagePurpose of the usage policy ({@code PPAP_EDC_POLICY_USAGE_PURPOSE}); individual values
     *     such as {@code msg.ppap.productApproval:1} are accepted by Tractus-X EDC 0.13 (verified), default {@code
     *     cx.quality.base:1} until a purpose is agreed (TODO(EG))
     * @param frameworkAgreement FrameworkAgreement of the usage policy
     * @param partnerGroup business partner group of the access policy
     */
    public record Policy(
            @DefaultValue("cx.quality.base:1") String usagePurpose,
            @DefaultValue("DataExchangeGovernance:1.0") String frameworkAgreement,
            @DefaultValue("ppap-partners") String partnerGroup) {}

    /**
     * Vault (KV v2) used to store the callback secret for the data plane. Empty URL: the secret is written into the
     * DataAddress as {@code authCode} instead (fallback, e.g. tests).
     */
    public record Vault(
            @Nullable String url,
            @Nullable String token,
            @DefaultValue("secret") String kvMount) {

        public boolean configured() {
            return url != null && !url.isBlank();
        }
    }
}
