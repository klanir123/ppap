package com.msg.ppap.platform.security;

import jakarta.validation.constraints.NotBlank;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * OpenID Connect settings.
 *
 * @param issuerUri expected {@code iss} claim, i.e. the public issuer URL ({@code PPAP_OIDC_ISSUER_URI})
 * @param jwkSetUri JWK set URL reachable from the backend, may use an internal host ({@code PPAP_OIDC_JWK_SET_URI})
 * @param publicAuthority authority the SPA uses for login; defaults to {@code issuerUri}
 *     ({@code PPAP_OIDC_PUBLIC_AUTHORITY})
 * @param clientId public OIDC client of the SPA ({@code PPAP_OIDC_CLIENT_ID})
 */
@Validated
@ConfigurationProperties("ppap.oidc")
public record OidcProperties(
        @NotBlank String issuerUri,
        @NotBlank String jwkSetUri,
        @Nullable String publicAuthority,
        @DefaultValue("ppap-ui") String clientId) {

    /** Authority published to the SPA. */
    public String effectiveAuthority() {
        return publicAuthority == null || publicAuthority.isBlank() ? issuerUri : publicAuthority;
    }
}
