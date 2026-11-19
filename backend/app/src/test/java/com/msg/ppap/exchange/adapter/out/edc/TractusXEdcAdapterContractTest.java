package com.msg.ppap.exchange.adapter.out.edc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.msg.ppap.exchange.application.port.out.AssetSpec;
import com.msg.ppap.exchange.application.port.out.ContractDefinitionSpec;
import com.msg.ppap.exchange.application.port.out.DataspaceException;
import com.msg.ppap.exchange.application.port.out.EndpointDataReference;
import com.msg.ppap.exchange.application.port.out.PartnerEndpoint;
import com.msg.ppap.exchange.application.port.out.PolicySpec;
import com.msg.ppap.exchange.config.EdcProperties;
import com.msg.ppap.exchange.domain.StepName;
import com.msg.ppap.exchange.domain.StepStatus;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Contract tests of the Tractus-X EDC 0.13 adapter against recorded management API responses
 * ({@code src/test/resources/edc}): request paths, JSON-LD bodies and response parsing.
 */
class TractusXEdcAdapterContractTest {

    static final JsonMapper JSON = JsonMapper.builder().build();
    static final PartnerEndpoint TIER1 = new PartnerEndpoint(
            "BPNL000000TIER01", "did:web:wallet-stub:BPNL000000TIER01", "http://tier1-edc-cp:8084/api/v1/dsp/2025-1");

    HttpServer server;
    final Map<String, Deque<Response>> responses = new HashMap<>();
    final List<Recorded> requests = new ArrayList<>();
    TractusXEdcAdapter adapter;

    record Response(int status, String body) {}

    record Recorded(String method, String path, String query, Map<String, String> headers, JsonNode body) {}

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String raw = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            var headers = new HashMap<String, String>();
            exchange.getRequestHeaders().forEach((k, v) -> headers.put(k.toLowerCase(), v.getFirst()));
            String path = exchange.getRequestURI().getPath();
            synchronized (requests) {
                requests.add(new Recorded(
                        exchange.getRequestMethod(),
                        path,
                        exchange.getRequestURI().getQuery(),
                        headers,
                        raw.isBlank() ? null : JSON.readTree(raw)));
            }
            Deque<Response> queue = responses.get(exchange.getRequestMethod() + " " + path);
            Response response = queue == null || queue.isEmpty()
                    ? new Response(404, "[]")
                    : (queue.size() > 1 ? queue.poll() : queue.peek());
            byte[] bytes = response.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(response.status(), bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                exchange.getResponseBody().write(bytes);
            }
            exchange.close();
        });
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        adapter = new TractusXEdcAdapter(new EdcProperties(
                URI.create(base + "/management"),
                "test-key",
                null,
                null,
                "http://oem-app:8080",
                "secret",
                "X-Ppap-Edc-Secret",
                "ppap-edc-callback-secret",
                Duration.ofSeconds(5),
                Duration.ofSeconds(5),
                Duration.ofMillis(10),
                new EdcProperties.Policy("cx.quality.base:1", "DataExchangeGovernance:1.0", "ppap-partners"),
                new EdcProperties.Vault(null, null, "secret")));
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    void on(String methodAndPath, int status, String body) {
        responses.computeIfAbsent(methodAndPath, k -> new ArrayDeque<>()).add(new Response(status, body));
    }

    static String fixture(String name) throws IOException {
        try (InputStream in = TractusXEdcAdapterContractTest.class.getResourceAsStream("/edc/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    Recorded request(String method, String path) {
        return requests.stream()
                .filter(r -> r.method().equals(method) && r.path().equals(path))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no " + method + " " + path + " in " + requests));
    }

    @Test
    void healthUsesApiKeyAndReportsUpAndDown() {
        on("POST /management/v3/assets/request", 200, "[]");
        assertThat(adapter.health().reachable()).isTrue();
        assertThat(request("POST", "/management/v3/assets/request").headers()).containsEntry("x-api-key", "test-key");
        responses.clear();
        on("POST /management/v3/assets/request", 401, "[]");
        assertThat(adapter.health().reachable()).isFalse();
        server.stop(0);
        var down = adapter.health();
        assertThat(down.reachable()).isFalse();
        assertThat(down.error()).isNotBlank();
    }

    @Test
    void assetIsCreatedOrUpdated() {
        on("POST /management/v3/assets", 409, "[]");
        on("PUT /management/v3/assets", 204, "");
        adapter.ensureAsset(new AssetSpec(
                "ppap-notification-receive",
                "cx-taxo:PpapNotificationAPI",
                "0.1",
                "desc",
                Map.of(
                        "type",
                        "HttpData",
                        "baseUrl",
                        "http://oem-app:8080/internal/edc/notifications",
                        "authKey",
                        "X-Ppap-Edc-Secret",
                        "secretName",
                        "ppap-edc-callback-secret")));
        JsonNode body = request("PUT", "/management/v3/assets").body();
        assertThat(body.path("@id").asString()).isEqualTo("ppap-notification-receive");
        assertThat(body.path("@context").path("cx-taxo").asString()).isEqualTo("https://w3id.org/catenax/taxonomy#");
        assertThat(body.path("properties").path("dct:type").path("@id").asString())
                .isEqualTo("cx-taxo:PpapNotificationAPI");
        assertThat(body.path("properties").path("cx-common:version").asString()).isEqualTo("0.1");
        assertThat(body.path("dataAddress").path("@type").asString()).isEqualTo("DataAddress");
        assertThat(body.path("dataAddress").path("secretName").asString()).isEqualTo("ppap-edc-callback-secret");
        assertThat(body.path("dataAddress").has("authCode")).isFalse();

        on("POST /management/v3/contractdefinitions", 500, "[{\"message\":\"boom\"}]");
        assertThatThrownBy(() -> adapter.ensureContractDefinition(new ContractDefinitionSpec("cd", "a", "u", "x")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HTTP 500");
    }

    @Test
    void policiesUseCxContextAndExistingReferencedPoliciesAreAccepted() throws IOException {
        on("POST /management/v3/policydefinitions", 400, fixture("policy-referenced-response.json"));
        adapter.ensurePolicy(new PolicySpec(
                "ppap-usage-1234abcd",
                "use",
                List.of(
                        new PolicySpec.Constraint("FrameworkAgreement", "eq", "DataExchangeGovernance:1.0"),
                        new PolicySpec.Constraint("UsagePurpose", "isAnyOf", List.of("cx.quality.base:1")))));
        JsonNode body = request("POST", "/management/v3/policydefinitions").body();
        assertThat(body.path("@context").get(0).asString()).isEqualTo(JsonLd.ODRL_PROFILE_CONTEXT);
        assertThat(body.path("@context").get(1).asString()).isEqualTo(JsonLd.CX_POLICY_CONTEXT);
        JsonNode permission = body.path("policy").path("permission").get(0);
        assertThat(permission.path("action").asString()).isEqualTo("use");
        assertThat(permission.path("constraint").path("and")).hasSize(2);
        assertThat(permission
                        .path("constraint")
                        .path("and")
                        .get(1)
                        .path("rightOperand")
                        .get(0)
                        .asString())
                .isEqualTo("cx.quality.base:1");

        responses.clear();
        requests.clear();
        on("POST /management/v3/policydefinitions", 200, "{}");
        adapter.ensurePolicy(new PolicySpec(
                "ppap-access-partners-1",
                "access",
                List.of(new PolicySpec.Constraint("BusinessPartnerGroup", "isAnyOf", List.of("ppap-partners")))));
        JsonNode single = request("POST", "/management/v3/policydefinitions").body();
        assertThat(single.path("policy")
                        .path("permission")
                        .get(0)
                        .path("constraint")
                        .path("leftOperand")
                        .asString())
                .isEqualTo("BusinessPartnerGroup");

        on("POST /management/v3/contractdefinitions", 200, "{}");
        adapter.ensureContractDefinition(new ContractDefinitionSpec(
                "ppap-notification-cd", "ppap-access-partners-1", "ppap-usage-1234abcd", "ppap-notification-receive"));
        JsonNode cd = request("POST", "/management/v3/contractdefinitions").body();
        assertThat(cd.path("assetsSelector").get(0).path("operandLeft").asString())
                .isEqualTo("https://w3id.org/edc/v0.0.1/ns/id");
        assertThat(cd.path("assetsSelector").get(0).path("operandRight").asString())
                .isEqualTo("ppap-notification-receive");
    }

    @Test
    void groupMembershipIsReconciledKeepingOtherGroups() throws IOException {
        on("GET /management/v3/business-partner-groups/group/ppap-partners", 200, fixture("group-response.json"));
        on("GET /management/v3/business-partner-groups/BPNL0000000NEW01", 404, "[]");
        on("POST /management/v3/business-partner-groups", 204, "");
        on("GET /management/v3/business-partner-groups/BPNL0000000OLD01", 200, fixture("bpn-groups-response.json"));
        on("PUT /management/v3/business-partner-groups", 204, "");

        Set<String> members = adapter.ensureBusinessPartnerGroup("ppap-partners", Set.of("BPNL0000000NEW01"));

        assertThat(members).containsExactly("BPNL0000000NEW01");
        JsonNode created =
                request("POST", "/management/v3/business-partner-groups").body();
        assertThat(created.path("@id").asString()).isEqualTo("BPNL0000000NEW01");
        assertThat(created.path("tx:groups").get(0).asString()).isEqualTo("ppap-partners");
        JsonNode updated =
                request("PUT", "/management/v3/business-partner-groups").body();
        assertThat(updated.path("@id").asString()).isEqualTo("BPNL0000000OLD01");
        assertThat(updated.path("tx:groups")).hasSize(1);
        assertThat(updated.path("tx:groups").get(0).asString()).isEqualTo("other-group");
    }

    @Test
    void negotiatesWhenNoEdrIsStoredAndReportsEachStep() throws IOException {
        on("POST /management/v3/edrs/request", 200, "[]");
        on("POST /management/v3/edrs/request", 200, fixture("edrs-response.json"));
        on("POST /management/v3/catalog/request", 200, fixture("catalog-response.json"));
        on("POST /management/v3/edrs", 200, fixture("edr-created-response.json"));
        on("GET /management/v3/contractnegotiations/neg-1", 200, fixture("negotiation-finalized-response.json"));
        on("GET /management/v3/edrs/tp-1/dataaddress", 200, fixture("dataaddress-response.json"));
        var steps = new ArrayList<String>();

        EndpointDataReference edr = adapter.obtainEndpoint(
                TIER1,
                "ppap-notification-receive",
                (step, status, duration, details) -> steps.add(step + ":" + status));

        assertThat(edr.endpoint()).isEqualTo(URI.create("http://tier1-edc-dp:8081/api/public"));
        assertThat(edr.authorization()).isEqualTo("recorded-token");
        assertThat(edr.cached()).isFalse();
        assertThat(edr.transferProcessId()).isEqualTo("tp-1");
        assertThat(steps).containsExactly("CATALOG:OK", "NEGOTIATION:OK", "TRANSFER:OK", "EDR:OK");

        JsonNode catalog = request("POST", "/management/v3/catalog/request").body();
        assertThat(catalog.path("counterPartyId").asString()).isEqualTo(TIER1.did());
        assertThat(catalog.path("counterPartyAddress").asString()).isEqualTo(TIER1.dspEndpoint());
        assertThat(catalog.path("protocol").asString()).isEqualTo("dataspace-protocol-http:2025-1");
        JsonNode contract = request("POST", "/management/v3/edrs").body();
        assertThat(contract.path("@type").asString()).isEqualTo("ContractRequest");
        assertThat(contract.path("policy").path("assigner").asString()).isEqualTo(TIER1.did());
        assertThat(contract.path("policy").path("target").asString()).isEqualTo("ppap-notification-receive");
        assertThat(contract.path("policy").path("@id").asString()).startsWith("cHJvYmUtY2Q=");
        assertThat(contract.path("policy").has("@context")).isFalse();
        assertThat(request("GET", "/management/v3/edrs/tp-1/dataaddress").query())
                .isEqualTo("auto_refresh=true");
    }

    @Test
    void reusesStoredEdrOfTheSameProvider() throws IOException {
        on("POST /management/v3/edrs/request", 200, fixture("edrs-response.json"));
        on("GET /management/v3/edrs/tp-1/dataaddress", 200, fixture("dataaddress-response.json"));
        var steps = new ArrayList<String>();

        EndpointDataReference edr = adapter.obtainEndpoint(
                TIER1,
                "ppap-notification-receive",
                (step, status, duration, details) -> steps.add(step + ":" + status));

        assertThat(edr.cached()).isTrue();
        assertThat(steps).containsExactly("CATALOG:SKIPPED", "NEGOTIATION:SKIPPED", "TRANSFER:SKIPPED", "EDR:OK");
        JsonNode query = request("POST", "/management/v3/edrs/request").body();
        assertThat(query.path("filterExpression").toString())
                .contains("providerId")
                .contains(TIER1.did());

        on("DELETE /management/v3/edrs/tp-1", 204, "");
        adapter.invalidateEndpoint(TIER1, "ppap-notification-receive");
        assertThat(request("DELETE", "/management/v3/edrs/tp-1")).isNotNull();
    }

    @Test
    void reportsCatalogAndNegotiationFailures() throws IOException {
        on("POST /management/v3/edrs/request", 200, "[]");
        on("POST /management/v3/catalog/request", 200, "{\"@type\":\"Catalog\",\"dataset\":[]}");
        var steps = new ArrayList<String>();
        assertThatThrownBy(() -> adapter.obtainEndpoint(
                        TIER1, "ppap-notification-receive", (s, st, d, det) -> steps.add(s + ":" + st)))
                .isInstanceOf(DataspaceException.class)
                .hasMessageContaining("not offered")
                .satisfies(ex -> assertThat(((DataspaceException) ex).step()).isEqualTo(StepName.CATALOG));
        assertThat(steps).containsExactly("CATALOG:FAILED");

        responses.clear();
        steps.clear();
        on("POST /management/v3/edrs/request", 200, "[]");
        on("POST /management/v3/catalog/request", 200, fixture("catalog-response.json"));
        on("POST /management/v3/edrs", 200, fixture("edr-created-response.json"));
        on(
                "GET /management/v3/contractnegotiations/neg-1",
                200,
                "{\"@type\":\"ContractNegotiation\",\"state\":\"TERMINATED\",\"errorDetail\":\"policy not fulfilled\"}");
        assertThatThrownBy(() -> adapter.obtainEndpoint(
                        TIER1, "ppap-notification-receive", (s, st, d, det) -> steps.add(s + ":" + st)))
                .isInstanceOf(DataspaceException.class)
                .hasMessageContaining("policy not fulfilled");
        assertThat(steps).containsExactly("CATALOG:OK", "NEGOTIATION:FAILED");
    }

    @Test
    void callsTheDataPlaneWithTheEdrToken() {
        on("POST /api/public", 200, "{\"status\":\"ACCEPTED\"}");
        var edr = new EndpointDataReference(
                "tp-1",
                "a-1",
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/api/public"),
                "tok",
                true);
        var response = adapter.call(
                edr,
                "POST",
                "{}".getBytes(StandardCharsets.UTF_8),
                "application/json",
                Map.of("X-Correlation-Id", "c-1"));
        assertThat(response.status()).isEqualTo(200);
        assertThat(response.successful()).isTrue();
        Recorded call = request("POST", "/api/public");
        assertThat(call.headers()).containsEntry("authorization", "tok").containsEntry("x-correlation-id", "c-1");
        server.stop(0);
        assertThatThrownBy(() -> adapter.call(edr, "GET", null, null, Map.of())).isInstanceOf(DataspaceException.class);
    }

    @Test
    void listsNegotiationsAndTransfers() {
        on(
                "POST /management/v3/contractnegotiations/request",
                200,
                "[{\"@id\":\"n1\",\"state\":\"FINALIZED\",\"@context\":{}}]");
        on("POST /management/v3/transferprocesses/request", 200, "[]");
        assertThat(adapter.listNegotiations(5))
                .singleElement()
                .satisfies(n -> assertThat(n).containsEntry("@id", "n1").doesNotContainKey("@context"));
        assertThat(adapter.listTransfers(5)).isEmpty();
        assertThat(request("POST", "/management/v3/contractnegotiations/request")
                        .body()
                        .path("limit")
                        .asInt())
                .isEqualTo(5);
        assertThat(StepStatus.values()).isNotEmpty();
    }

    @Test
    void findsTheDtrOfferByTaxonomyTypeAndNegotiatesItWithoutSecondCatalog() throws IOException {
        on("POST /management/v3/catalog/request", 200, fixture("catalog-dtr-response.json"));
        var offers = adapter.requestCatalogByType(TIER1, "cx-taxo:DigitalTwinRegistry");
        assertThat(offers).hasSize(1);
        assertThat(offers.getFirst().assetId()).isEqualTo("ppap-dtr");
        JsonNode filter = request("POST", "/management/v3/catalog/request")
                .body()
                .path("querySpec")
                .path("filterExpression")
                .path(0);
        assertThat(filter.path("operandLeft").asString()).isEqualTo("'http://purl.org/dc/terms/type'.'@id'");
        assertThat(filter.path("operandRight").asString())
                .isEqualTo("https://w3id.org/catenax/taxonomy#DigitalTwinRegistry");
        assertThat(TractusXEdcAdapter.expandTaxonomy("cx-taxo:Submodel"))
                .isEqualTo("https://w3id.org/catenax/taxonomy#Submodel");

        // a stored EDR of the DTR asset is reused (no negotiation)
        on("POST /management/v3/edrs/request", 200, fixture("edrs-response.json"));
        on("GET /management/v3/edrs/tp-1/dataaddress", 200, fixture("dataaddress-response.json"));
        var steps = new ArrayList<String>();
        EndpointDataReference edr = adapter.obtainEndpoint(
                TIER1, offers.getFirst(), (step, status, duration, details) -> steps.add(step + ":" + status));
        assertThat(edr.cached()).isTrue();
        assertThat(steps).contains("CATALOG:SKIPPED", "EDR:OK");
    }

    @Test
    void resolvesDataPlanePathsAndListsAndRemovesOffers() {
        var edr = new EndpointDataReference("tp", "ag", URI.create("http://dp/api/public/"), "tok", false);
        assertThat(TractusXEdcAdapter.resolve(edr, "/lookup/shells?assetIds=x").toString())
                .isEqualTo("http://dp/api/public/lookup/shells?assetIds=x");
        assertThat(TractusXEdcAdapter.resolve(edr, "a/b").toString()).isEqualTo("http://dp/api/public/a/b");
        assertThat(TractusXEdcAdapter.resolve(edr, "")).isEqualTo(edr.endpoint());
        on(
                "POST /management/v3/contractdefinitions/request",
                200,
                "[{\"@id\":\"ppap-documents-X-cd\"},{\"@id\":\"other\"}]");
        assertThat(adapter.listContractDefinitionIds()).containsExactly("ppap-documents-X-cd", "other");
        on("DELETE /management/v3/contractdefinitions/ppap-documents-X-cd", 204, "");
        on("DELETE /management/v3/assets/ppap-documents-X", 409, "[]");
        adapter.removeOffer("ppap-documents-X-cd", "ppap-documents-X");
        assertThat(request("DELETE", "/management/v3/assets/ppap-documents-X")).isNotNull();
    }
}
