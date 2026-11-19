package com.msg.ppap.twin.config;

import java.time.Duration;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Own Digital Twin Registry (docs/api/pull-api.md, „Konfiguration“).
 *
 * @param url DTR base URL for the backend ({@code PPAP_DTR_URL}, e.g. {@code http://tier1-dtr:4243/api/v3}); empty:
 *     no DTR (PUSH only)
 * @param tokenUrl Keycloak token endpoint ({@code PPAP_DTR_TOKEN_URL})
 * @param clientId backend client with DTR write roles ({@code PPAP_DTR_CLIENT_ID}, {@code ppap-backend-dtr})
 * @param clientSecret its secret ({@code PPAP_DTR_CLIENT_SECRET})
 * @param dataplanePublicUrl own data plane public API as seen by partners ({@code PPAP_EDC_DATAPLANE_PUBLIC_URL}),
 *     base of the submodel {@code href}
 * @param dspUrl own DSP endpoint ({@code subprotocolBody}), defaults to {@code ppap.edc.dsp-url}
 * @param requestTimeout timeout of DTR and token calls
 */
@ConfigurationProperties("ppap.dtr")
public record TwinProperties(
        @Nullable String url,
        @Nullable String tokenUrl,
        @DefaultValue("ppap-backend-dtr") String clientId,
        @Nullable String clientSecret,
        @Nullable String dataplanePublicUrl,
        @Nullable String dspUrl,
        @DefaultValue("10s") Duration requestTimeout) {

    /** Whether a DTR is configured. */
    public boolean configured() {
        return url != null && !url.isBlank();
    }
}
