package com.msg.ppap.twin.adapter.out.dtr;

import com.msg.ppap.identity.TenantContext;
import com.msg.ppap.semantics.api.AasDescriptors;
import com.msg.ppap.twin.application.port.out.TwinRegistry;
import com.msg.ppap.twin.application.port.out.TwinRegistryException;
import com.msg.ppap.twin.config.TwinProperties;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@link TwinRegistry} for the Tractus-X Digital Twin Registry 0.13 (AAS Registry API 3.1, base path {@code
 * /api/v3}). Authentication: OAuth2 client credentials at Keycloak (client {@code ppap-backend-dtr} with the DTR client
 * roles), token cached until shortly before expiry. Every call carries {@code Edc-Bpn = own BPNL}: the DTR treats the
 * header as tenant of the caller ({@code externalSubjectId} visibility) and shows the owning tenant ({@code
 * REGISTRY_IDM_OWNING_TENANT_ID}) all twins – verified against DTR 0.13 (without header the owner sees nothing).
 * Identifiers in paths are base64url-encoded.
 */
@Component
class DtrRegistryClient implements TwinRegistry {

    static final String TENANT_HEADER = "Edc-Bpn";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final TwinProperties properties;
    private final TenantContext tenant;
    private final Clock clock;
    private final HttpClient http;
    private volatile String token;
    private volatile Instant tokenExpiry = Instant.EPOCH;

    DtrRegistryClient(TwinProperties properties, TenantContext tenant, Clock clock) {
        this.properties = properties;
        this.tenant = tenant;
        this.clock = clock;
        this.http = HttpClient.newBuilder()
                .proxy(HttpClient.Builder.NO_PROXY)
                .connectTimeout(properties.requestTimeout())
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    @Override
    public boolean configured() {
        return properties.configured();
    }

    @Override
    public Optional<byte[]> findShell(String shellId) {
        HttpResponse<byte[]> response = send(request("/shell-descriptors/" + AasDescriptors.encodeId(shellId))
                .GET());
        if (response.statusCode() == 404) {
            return Optional.empty();
        }
        check(response, "read shell " + shellId);
        return Optional.of(response.body());
    }

    @Override
    public void putShell(String shellId, byte[] descriptor) {
        HttpResponse<byte[]> response = send(request("/shell-descriptors/" + AasDescriptors.encodeId(shellId))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofByteArray(descriptor)));
        check(response, "register shell " + shellId);
    }

    @Override
    public boolean reachable() {
        try {
            HttpResponse<byte[]> response =
                    send(request("/shell-descriptors?limit=1").GET());
            return response.statusCode() < 300;
        } catch (TwinRegistryException ex) {
            return false;
        }
    }

    private HttpRequest.Builder request(String path) {
        if (!properties.configured()) {
            throw new TwinRegistryException("No Digital Twin Registry configured (PPAP_DTR_URL)", null, null);
        }
        return HttpRequest.newBuilder(URI.create(properties.url().replaceAll("/+$", "") + path))
                .timeout(properties.requestTimeout())
                .header("Authorization", "Bearer " + token())
                .header(TENANT_HEADER, tenant.tenantId())
                .header("Accept", "application/json");
    }

    private HttpResponse<byte[]> send(HttpRequest.Builder request) {
        try {
            HttpResponse<byte[]> response = http.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() == 401) {
                tokenExpiry = Instant.EPOCH;
            }
            return response;
        } catch (IOException ex) {
            throw new TwinRegistryException("DTR not reachable: " + ex, null, ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new TwinRegistryException("interrupted", null, ex);
        }
    }

    private static void check(HttpResponse<byte[]> response, String action) {
        if (response.statusCode() >= 300) {
            String body = new String(response.body(), StandardCharsets.UTF_8);
            throw new TwinRegistryException(
                    "DTR: cannot " + action + ": HTTP " + response.statusCode() + " "
                            + (body.length() > 500 ? body.substring(0, 500) + "…" : body),
                    response.statusCode(),
                    null);
        }
    }

    /** Client credentials token, cached until 30 s before expiry. */
    private String token() {
        Instant now = clock.instant();
        if (token != null && now.isBefore(tokenExpiry)) {
            return token;
        }
        synchronized (this) {
            if (token != null && clock.instant().isBefore(tokenExpiry)) {
                return token;
            }
            if (properties.tokenUrl() == null || properties.tokenUrl().isBlank()) {
                throw new TwinRegistryException("No token URL for the DTR configured (PPAP_DTR_TOKEN_URL)", null, null);
            }
            String form = "grant_type=client_credentials&client_id=" + encode(properties.clientId()) + "&client_secret="
                    + encode(properties.clientSecret() == null ? "" : properties.clientSecret());
            HttpResponse<byte[]> response;
            try {
                response = http.send(
                        HttpRequest.newBuilder(URI.create(properties.tokenUrl()))
                                .timeout(properties.requestTimeout())
                                .header("Content-Type", "application/x-www-form-urlencoded")
                                .POST(HttpRequest.BodyPublishers.ofString(form))
                                .build(),
                        HttpResponse.BodyHandlers.ofByteArray());
            } catch (IOException ex) {
                throw new TwinRegistryException("Token endpoint not reachable: " + ex, null, ex);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new TwinRegistryException("interrupted", null, ex);
            }
            if (response.statusCode() != 200) {
                throw new TwinRegistryException(
                        "Token request for " + properties.clientId() + " failed: HTTP " + response.statusCode(),
                        response.statusCode(),
                        null);
            }
            JsonNode body = JSON.readTree(response.body());
            token = body.path("access_token").asString();
            long expiresIn = body.path("expires_in").asLong(60);
            tokenExpiry = clock.instant().plusSeconds(Math.max(5, expiresIn - 30));
            return token;
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
