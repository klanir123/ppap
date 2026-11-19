package com.msg.ppap.identity.adapter.out.signing;

import com.msg.ppap.identity.application.port.out.SigningKeyProvider;
import com.msg.ppap.identity.config.SigningProperties;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads the signing key from OpenBao/HashiCorp Vault KV v2:
 * {@code GET <PPAP_VAULT_URL>/v1/<mount>/data/<PPAP_SIGNING_KEY_PATH>} with {@code X-Vault-Token}; the PEM (PKCS#8, EC
 * P-256) is expected in {@code data.data.<field>} (default {@code content}).
 */
class VaultSigningKeyProvider implements SigningKeyProvider {

    private final SigningProperties.Vault vault;
    private final JsonMapper jsonMapper;

    VaultSigningKeyProvider(SigningProperties.Vault vault, JsonMapper jsonMapper) {
        this.vault = vault;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public SigningKey load() {
        if (vault.url() == null
                || vault.url().toString().isBlank()
                || vault.token() == null
                || vault.token().isBlank()) {
            throw new IllegalStateException("ppap.signing.provider=vault requires PPAP_VAULT_URL and PPAP_VAULT_TOKEN "
                    + "(or PPAP_SIGNING_PROVIDER=file with PPAP_SIGNING_KEY_FILE)");
        }
        URI uri = secretUri();
        String pem;
        try (HttpClient client =
                HttpClient.newBuilder().connectTimeout(vault.timeout()).build()) {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(vault.timeout())
                    .header("X-Vault-Token", vault.token())
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException(
                        "Signing key cannot be read from " + uri + ": HTTP " + response.statusCode());
            }
            JsonNode field = jsonMapper
                    .readTree(response.body())
                    .path("data")
                    .path("data")
                    .path(vault.field());
            if (!field.isString() || field.asString().isBlank()) {
                throw new IllegalStateException(
                        "Secret " + uri + " has no field '" + vault.field() + "' with a PEM encoded private key");
            }
            pem = field.asString();
        } catch (IOException ex) {
            throw new IllegalStateException("Signing key cannot be read from " + uri + ": " + ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while reading the signing key from " + uri, ex);
        }
        try {
            var privateKey = EcKeys.parsePkcs8Pem(pem);
            return new SigningKey(privateKey, EcKeys.publicKeyOf(privateKey));
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Secret " + uri + " holds no usable key: " + ex.getMessage(), ex);
        }
    }

    @Override
    public String description() {
        return "vault:" + vault.kvMount() + "/" + vault.keyPath();
    }

    URI secretUri() {
        String base = vault.url().toString().replaceAll("/+$", "");
        return URI.create(base + "/v1/" + vault.kvMount() + "/data/" + vault.keyPath());
    }
}
