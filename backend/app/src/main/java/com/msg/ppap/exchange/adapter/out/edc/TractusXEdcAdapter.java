package com.msg.ppap.exchange.adapter.out.edc;

import com.msg.ppap.exchange.application.port.out.ConnectorHealth;
import com.msg.ppap.exchange.application.port.out.DataspaceConnector;
import com.msg.ppap.exchange.config.EdcProperties;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/** {@link DataspaceConnector} implementation for the Tractus-X EDC management API (v3). */
@Component
public class TractusXEdcAdapter implements DataspaceConnector {

    static final Duration TIMEOUT = Duration.ofSeconds(3);
    static final String API_KEY_HEADER = "X-Api-Key";
    static final String ASSET_QUERY_PATH = "/v3/assets/request";
    static final String MINIMAL_QUERY = """
            {"@context":{"@vocab":"https://w3id.org/edc/v0.0.1/ns/"},"@type":"QuerySpec","limit":1}""";

    private final EdcProperties properties;
    private final RestClient restClient;

    public TractusXEdcAdapter(EdcProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        // JDK client without proxy: the EDC lives in the participant's internal network.
        var httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(TIMEOUT);
        this.restClient =
                restClientBuilder.clone().requestFactory(requestFactory).build();
    }

    @Override
    public ConnectorHealth health() {
        URI uri = managementUri(ASSET_QUERY_PATH);
        long start = System.nanoTime();
        try {
            restClient
                    .post()
                    .uri(uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .headers(headers -> {
                        if (properties.apiKey() != null && !properties.apiKey().isBlank()) {
                            headers.set(API_KEY_HEADER, properties.apiKey());
                        }
                    })
                    .body(MINIMAL_QUERY)
                    .retrieve()
                    .toBodilessEntity();
            return ConnectorHealth.up(uri.toString(), elapsedSince(start));
        } catch (RuntimeException ex) {
            return ConnectorHealth.down(uri.toString(), elapsedSince(start), describe(ex));
        }
    }

    private URI managementUri(String path) {
        return UriComponentsBuilder.fromUri(properties.managementUrl())
                .path(path)
                .build()
                .toUri();
    }

    private static Duration elapsedSince(long startNanos) {
        return Duration.ofNanos(System.nanoTime() - startNanos);
    }

    private static String describe(Throwable ex) {
        Throwable root = ex;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage() != null ? root.getMessage() : "";
        return root.getClass().getSimpleName() + (message.isBlank() ? "" : ": " + message);
    }
}
