package com.msg.ppap.identity.config;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Source of the tenant signing key (ADR-0008).
 *
 * @param provider {@code vault} (default, OpenBao/Vault KV v2), {@code file} or {@code generated} (development and
 *     tests only: a new key on every start) ({@code PPAP_SIGNING_PROVIDER})
 * @param keyFile PEM file (PKCS#8, EC P-256) for provider {@code file} ({@code PPAP_SIGNING_KEY_FILE})
 * @param vault OpenBao/Vault settings for provider {@code vault}
 */
@ConfigurationProperties("ppap.signing")
public record SigningProperties(
        @DefaultValue("vault") Provider provider,
        @Nullable Path keyFile,
        @DefaultValue Vault vault) {

    /** Key sources. */
    public enum Provider {
        VAULT,
        FILE,
        GENERATED
    }

    /**
     * OpenBao/Vault KV v2 access: {@code GET <url>/v1/<kvMount>/data/<keyPath>}, PEM in field {@code field}.
     *
     * @param url base URL, e.g. {@code http://oem-vault:8200} ({@code PPAP_VAULT_URL})
     * @param token access token ({@code PPAP_VAULT_TOKEN})
     * @param kvMount mount of the KV v2 engine ({@code PPAP_VAULT_KV_MOUNT}, default {@code secret})
     * @param keyPath secret path ({@code PPAP_SIGNING_KEY_PATH}, default {@code ppap-signing-key})
     * @param field secret field holding the PEM ({@code content})
     * @param timeout connect and read timeout
     */
    public record Vault(
            @Nullable URI url,
            @Nullable String token,
            @DefaultValue("secret") String kvMount,
            @DefaultValue("ppap-signing-key") String keyPath,
            @DefaultValue("content") String field,
            @DefaultValue("5s") Duration timeout) {}
}
