package com.msg.ppap.exchange.config;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Offer of the own Digital Twin Registry in the dataspace (asset {@code ppap-dtr}, docs/api/pull-api.md): how the own
 * data plane reaches the DTR and authenticates (OAuth2 client credentials). The backend's own DTR client is configured
 * in the twin module ({@code ppap.dtr.url}, {@code client-id}, …).
 *
 * @param publicUrl DTR base URL as seen by the own data plane ({@code PPAP_DTR_PUBLIC_URL}, e.g. {@code
 *     http://tier1-dtr:4243/api/v3}); empty: no DTR, no pull offers (PUSH only)
 * @param tokenUrl Keycloak token endpoint for the data plane ({@code PPAP_DTR_TOKEN_URL})
 * @param edcClientId client of the data plane ({@code PPAP_DTR_EDC_CLIENT_ID}, read-only DTR role)
 * @param edcClientSecret its secret ({@code PPAP_DTR_EDC_CLIENT_SECRET}); stored in the participant's vault
 * @param edcClientSecretName vault secret the data plane resolves ({@code oauth2:clientSecretKey})
 */
@ConfigurationProperties("ppap.dtr")
public record DtrOfferProperties(
        @Nullable String publicUrl,
        @Nullable String tokenUrl,
        @DefaultValue("ppap-edc-dtr") String edcClientId,
        @Nullable String edcClientSecret,
        @DefaultValue("ppap-dtr-edc-client-secret") String edcClientSecretName) {

    /** Whether the own DTR is offered (supplier side pull). */
    public boolean offered() {
        return publicUrl != null && !publicUrl.isBlank();
    }
}
