package com.msg.ppap.twin.adapter.out.dtr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.msg.ppap.identity.TenantContext;
import com.msg.ppap.semantics.api.AasDescriptors;
import com.msg.ppap.twin.application.port.out.TwinRegistryException;
import com.msg.ppap.twin.config.TwinProperties;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Contract test of the DTR 0.13 client against a stub serving recorded AAS JSON ({@code src/test/resources/dtr}):
 * client credentials token (cached), {@code Edc-Bpn} = own BPNL (owner view), base64url identifiers, PUT of the
 * shell descriptor, 404 → not registered, error classification.
 */
class DtrRegistryClientContractTest {

    static final String SHELL_ID = "urn:uuid:0963e641-70f5-4542-83e8-de75ebcfce9f";

    HttpServer server;
    final List<Map<String, String>> requests = new ArrayList<>();
    final Map<String, Integer> statuses = new HashMap<>();
    int tokenRequests;
    DtrRegistryClient client;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/token", exchange -> {
            tokenRequests++;
            String form = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            byte[] body = form.contains("client_secret=s3cret")
                    ? "{\"access_token\":\"tok\",\"expires_in\":300}".getBytes(StandardCharsets.UTF_8)
                    : "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(form.contains("client_secret=s3cret") ? 200 : 401, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/api/v3", exchange -> {
            var r = new HashMap<String, String>();
            r.put("method", exchange.getRequestMethod());
            r.put("path", exchange.getRequestURI().getRawPath());
            r.put("authorization", exchange.getRequestHeaders().getFirst("Authorization"));
            r.put("edcBpn", exchange.getRequestHeaders().getFirst("Edc-Bpn"));
            r.put("body", new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            requests.add(r);
            int status = statuses.getOrDefault(exchange.getRequestMethod(), 200);
            byte[] body = status == 200 && "GET".equals(exchange.getRequestMethod()) ? fixture() : new byte[0];
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
            if (body.length > 0) {
                exchange.getResponseBody().write(body);
            }
            exchange.close();
        });
        server.start();
        client = client("s3cret");
    }

    DtrRegistryClient client(String secret) {
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        var tenant = mock(TenantContext.class);
        when(tenant.tenantId()).thenReturn("BPNL000000TIER01");
        return new DtrRegistryClient(
                new TwinProperties(
                        base + "/api/v3/",
                        base + "/token",
                        "ppap-backend-dtr",
                        secret,
                        null,
                        null,
                        Duration.ofSeconds(5)),
                tenant,
                Clock.systemUTC());
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void readsAndRegistersShellsAsOwnerWithCachedToken() {
        byte[] shell = client.findShell(SHELL_ID).orElseThrow();
        assertThat(AasDescriptors.parseShell(shell).id()).isEqualTo(SHELL_ID);
        statuses.put("PUT", 204);
        client.putShell(SHELL_ID, "{\"id\":\"x\"}".getBytes(StandardCharsets.UTF_8));

        String encoded = AasDescriptors.encodeId(SHELL_ID);
        assertThat(requests).hasSize(2).allSatisfy(r -> {
            assertThat(r.get("path")).isEqualTo("/api/v3/shell-descriptors/" + encoded);
            assertThat(r.get("authorization")).isEqualTo("Bearer tok");
            assertThat(r.get("edcBpn")).isEqualTo("BPNL000000TIER01");
        });
        assertThat(requests.get(1).get("method")).isEqualTo("PUT");
        assertThat(requests.get(1).get("body")).isEqualTo("{\"id\":\"x\"}");
        assertThat(tokenRequests).isEqualTo(1);
        assertThat(client.reachable()).isTrue();
        assertThat(client.configured()).isTrue();
    }

    @Test
    void missingShellIsEmptyAndErrorsAreClassified() {
        statuses.put("GET", 404);
        assertThat(client.findShell(SHELL_ID)).isEmpty();
        statuses.put("PUT", 400);
        assertThatThrownBy(() -> client.putShell(SHELL_ID, new byte[0]))
                .isInstanceOfSatisfying(TwinRegistryException.class, ex -> {
                    assertThat(ex.httpStatus()).isEqualTo(400);
                    assertThat(ex.retryable()).isFalse();
                });
        statuses.put("PUT", 503);
        assertThatThrownBy(() -> client.putShell(SHELL_ID, new byte[0]))
                .isInstanceOfSatisfying(
                        TwinRegistryException.class,
                        ex -> assertThat(ex.retryable()).isTrue());
    }

    @Test
    void tokenFailureAndUnreachableRegistry() {
        DtrRegistryClient wrong = client("wrong");
        assertThatThrownBy(() -> wrong.findShell(SHELL_ID))
                .isInstanceOfSatisfying(
                        TwinRegistryException.class,
                        ex -> assertThat(ex.httpStatus()).isEqualTo(401));
        server.stop(0);
        assertThat(client.reachable()).isFalse();
        var unconfigured = new DtrRegistryClient(
                new TwinProperties(null, null, "c", null, null, null, Duration.ofSeconds(1)),
                mock(TenantContext.class),
                Clock.systemUTC());
        assertThat(unconfigured.configured()).isFalse();
        assertThatThrownBy(() -> unconfigured.findShell(SHELL_ID)).isInstanceOf(TwinRegistryException.class);
    }

    static byte[] fixture() throws IOException {
        try (InputStream in = DtrRegistryClientContractTest.class.getResourceAsStream("/dtr/shell-descriptor.json")) {
            return in.readAllBytes();
        }
    }
}
