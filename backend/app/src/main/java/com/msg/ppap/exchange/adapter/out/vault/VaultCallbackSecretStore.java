package com.msg.ppap.exchange.adapter.out.vault;

import com.msg.ppap.exchange.application.port.out.CallbackSecretStore;
import com.msg.ppap.exchange.config.EdcProperties;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes the callback secret into the participant's HashiCorp Vault / OpenBao (KV v2, field {@code content}) – the
 * secret store from which the Tractus-X EDC data plane resolves the DataAddress {@code secretName}.
 */
@Component
class VaultCallbackSecretStore implements CallbackSecretStore {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final EdcProperties.Vault vault;
    private final HttpClient http;
    private final EdcProperties properties;

    VaultCallbackSecretStore(EdcProperties properties) {
        this.properties = properties;
        this.vault = properties.vault();
        this.http = HttpClient.newBuilder()
                .proxy(HttpClient.Builder.NO_PROXY)
                .connectTimeout(properties.requestTimeout())
                .build();
    }

    @Override
    public boolean available() {
        return vault.configured();
    }

    @Override
    public void store(String name, String value) {
        String base = vault.url().replaceAll("/+$", "");
        URI uri = URI.create(
                base + "/v1/" + vault.kvMount() + "/data/" + URLEncoder.encode(name, StandardCharsets.UTF_8));
        byte[] body = JSON.writeValueAsBytes(Map.of("data", Map.of("content", value)));
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(properties.requestTimeout())
                .header("X-Vault-Token", vault.token() == null ? "" : vault.token())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new IllegalStateException("Vault rejected the callback secret: HTTP " + response.statusCode());
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Vault not reachable: " + ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted", ex);
        }
    }
}
