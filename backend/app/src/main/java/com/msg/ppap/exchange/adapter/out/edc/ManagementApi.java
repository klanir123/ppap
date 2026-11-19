package com.msg.ppap.exchange.adapter.out.edc;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Minimal client of the EDC management API: JSON in, JSON out, HTTP status exposed (409/404 are part of the
 * idempotent provisioning protocol, not errors). JDK HTTP client without proxy – the EDC lives in the participant's
 * internal network.
 */
final class ManagementApi {

    static final String API_KEY_HEADER = "X-Api-Key";
    static final JsonMapper JSON = JsonMapper.builder().build();

    private final URI baseUrl;
    private final String apiKey;
    private final HttpClient http;
    private final Duration timeout;

    ManagementApi(URI baseUrl, String apiKey, Duration timeout) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.timeout = timeout;
        this.http = HttpClient.newBuilder()
                .proxy(HttpClient.Builder.NO_PROXY)
                .connectTimeout(timeout)
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    /** Response of the management API. */
    record Response(int status, JsonNode body, String raw) {

        boolean ok() {
            return status >= 200 && status < 300;
        }
    }

    URI uri(String path) {
        String base = baseUrl.toString().replaceAll("/+$", "");
        return URI.create(base + path);
    }

    Response get(String path) {
        return send("GET", path, null, timeout);
    }

    Response delete(String path) {
        return send("DELETE", path, null, timeout);
    }

    Response post(String path, Object body) {
        return send("POST", path, body, timeout);
    }

    Response put(String path, Object body) {
        return send("PUT", path, body, timeout);
    }

    Response send(String method, String path, Object body, Duration requestTimeout) {
        byte[] bytes = body == null ? null : JSON.writeValueAsBytes(body);
        HttpRequest.Builder request = HttpRequest.newBuilder(uri(path))
                .timeout(requestTimeout)
                .header("Accept", "application/json")
                .method(
                        method,
                        bytes == null
                                ? HttpRequest.BodyPublishers.noBody()
                                : HttpRequest.BodyPublishers.ofByteArray(bytes));
        if (bytes != null) {
            request.header("Content-Type", "application/json");
        }
        if (apiKey != null && !apiKey.isBlank()) {
            request.header(API_KEY_HEADER, apiKey);
        }
        try {
            HttpResponse<byte[]> response = http.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
            String raw = new String(response.body(), StandardCharsets.UTF_8);
            JsonNode node = null;
            if (!raw.isBlank()) {
                try {
                    node = JSON.readTree(raw);
                } catch (RuntimeException ex) {
                    node = null;
                }
            }
            return new Response(response.statusCode(), node, raw);
        } catch (IOException ex) {
            throw new ManagementApiUnavailable(method + " " + path + ": " + describe(ex), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ManagementApiUnavailable(method + " " + path + ": interrupted", ex);
        }
    }

    static String describe(Throwable ex) {
        Throwable root = ex;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage() != null ? root.getMessage() : "";
        return root.getClass().getSimpleName() + (message.isBlank() ? "" : ": " + message);
    }

    /** The management API could not be reached. */
    static final class ManagementApiUnavailable extends RuntimeException {

        ManagementApiUnavailable(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
