package com.msg.ppap.live;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * End-to-end test of the dataspace exchange against the running local stack ({@code make up PROFILE=tier2}): every
 * message travels OEM ⇄ Tier-1 through both EDCs (catalog, negotiation, EDR, data plane). Uses the technical client
 * {@code ppap-integration} of both realms (decisions/review by technical clients require {@code
 * PPAP_DECISION_ALLOW_TECHNICAL_CLIENTS=true}, set in compose for the demo participants).
 *
 * <p>Not part of {@code ./mvnw verify}; run with {@code ./mvnw -pl backend/app -am -Plive verify}. Override endpoints with
 * the system properties {@code live.keycloak}, {@code live.oem}, {@code live.tier1}.
 */
@Tag("live")
class ExchangeLiveTest {

    static final JsonMapper JSON = JsonMapper.builder().build();
    static final String KEYCLOAK = System.getProperty("live.keycloak", "http://localhost:8180");
    static final String OEM = System.getProperty("live.oem", "http://localhost:8081");
    static final String TIER1 = System.getProperty("live.tier1", "http://localhost:8082");
    static final String TIER1_BPNL = System.getProperty("live.tier1Bpnl", "BPNL000000TIER01");
    static final Duration TIMEOUT = Duration.ofSeconds(90);

    final HttpClient http = HttpClient.newBuilder()
            .proxy(HttpClient.Builder.NO_PROXY)
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    final Map<String, Long> timings = new LinkedHashMap<>();

    @Test
    void fullExchangeOemTier1() throws Exception {
        String oem = token("oem");
        String tier1 = token("tier1");

        // OEM creates and sends a request
        JsonNode mapping = stream(get(OEM + "/api/v1/part-mappings?partnerRole=SUPPLIER&size=200", oem)
                        .get("content"))
                .filter(m -> TIER1_BPNL.equals(m.path("partner").path("bpnl").asString()))
                .findFirst()
                .orElseThrow();
        JsonNode created = send(
                "POST",
                OEM + "/api/v1/approval-cases",
                oem,
                Map.of(
                        "partId",
                        mapping.path("part").path("id").asString(),
                        "relationId",
                        mapping.path("relationId").asString(),
                        "reasonCode",
                        "NEW_PART",
                        "levelCode",
                        "VDA-1",
                        "dueDate",
                        LocalDate.now().plusDays(30).toString(),
                        "remarks",
                        "live test " + UUID.randomUUID()));
        String oemCase = created.path("id").asString();
        String exchangeId = created.path("exchangeId").asString();
        long t = System.nanoTime();
        send("POST", OEM + "/api/v1/approval-cases/" + oemCase + "/send", oem, null);

        // Tier-1 receives the case
        String tier1Case = await(
                        "request reaches Tier-1",
                        t,
                        () -> stream(get(TIER1 + "/api/v1/approval-cases?size=200&ownRole=SUPPLIER", tier1)
                                        .get("content"))
                                .filter(c ->
                                        exchangeId.equals(c.path("exchangeId").asString()))
                                .findFirst()
                                .orElse(null))
                .path("id")
                .asString();
        JsonNode outbound = get(OEM + "/api/v1/approval-cases/" + oemCase + "/exchange", oem);
        assertThat(outbound.get(0).path("status").asString()).isEqualTo("DELIVERED");

        // Tier-1 starts work → OEM IN_PROGRESS
        t = System.nanoTime();
        send("POST", TIER1 + "/api/v1/approval-cases/" + tier1Case + "/start-work", tier1, null);
        awaitStatus("status IN_PROGRESS reaches OEM", t, OEM, oemCase, oem, "IN_PROGRESS");

        // fill required elements and submit → OEM SUBMITTED with VALID signature
        fillDraft(tier1Case, tier1);
        t = System.nanoTime();
        send(
                "POST",
                TIER1 + "/api/v1/approval-cases/" + tier1Case + "/submissions/draft/submit",
                tier1,
                Map.of("confirmation", true));
        JsonNode submitted = awaitStatus("submission reaches OEM", t, OEM, oemCase, oem, "SUBMITTED");
        assertThat(submitted
                        .path("submissions")
                        .get(0)
                        .path("signature")
                        .path("status")
                        .asString())
                .isEqualTo("VALID");

        // OEM reviews and rejects with a finding → Tier-1 REJECTED
        t = System.nanoTime();
        send("POST", OEM + "/api/v1/approval-cases/" + oemCase + "/start-review", oem, null);
        awaitStatus("status IN_REVIEW reaches Tier-1", t, TIER1, tier1Case, tier1, "IN_REVIEW");
        String element = submitted.path("submissions").get(0).path("revision").asInt() == 1
                ? get(OEM + "/api/v1/approval-cases/" + oemCase, oem)
                        .path("request")
                        .path("elements")
                        .get(0)
                        .path("code")
                        .asString()
                : null;
        send(
                "PUT",
                OEM + "/api/v1/approval-cases/" + oemCase + "/review/elements/" + element,
                oem,
                Map.of("result", "NOT_ACCEPTED", "comment", "live test finding"));
        t = System.nanoTime();
        send(
                "POST",
                OEM + "/api/v1/approval-cases/" + oemCase + "/decision",
                oem,
                Map.of("result", "REJECTED", "comment", "live test", "confirmation", true));
        JsonNode rejected = awaitStatus("decision reaches Tier-1", t, TIER1, tier1Case, tier1, "REJECTED");
        assertThat(rejected.path("lastDecision")
                        .path("signature")
                        .path("status")
                        .asString())
                .isEqualTo("VALID");

        // new revision → OEM IN_PROGRESS
        t = System.nanoTime();
        send("POST", TIER1 + "/api/v1/approval-cases/" + tier1Case + "/submissions", tier1, null);
        awaitStatus("new revision reaches OEM", t, OEM, oemCase, oem, "IN_PROGRESS");

        System.out.println("LIVE TIMINGS (ms): " + timings);
        for (JsonNode record : get(OEM + "/api/v1/approval-cases/" + oemCase + "/exchange", oem)) {
            System.out.println("  OEM " + record.path("direction").asString() + " "
                    + record.path("messageType").asString() + " "
                    + record.path("status").asString() + " steps="
                    + stream(record.path("steps"))
                            .map(s -> s.path("step").asString() + ":"
                                    + s.path("status").asString() + ":"
                                    + s.path("durationMs").asLong())
                            .toList());
        }
    }

    private void fillDraft(String caseId, String token) throws Exception {
        JsonNode detail = get(TIER1 + "/api/v1/approval-cases/" + caseId, token);
        String documentId = null;
        for (JsonNode element : detail.path("request").path("elements")) {
            if (!"REQUIRED".equals(element.path("requirement").asString())) {
                continue;
            }
            String code = element.path("code").asString();
            JsonNode snapshot = element.path("snapshot");
            var body = new LinkedHashMap<String, Object>();
            body.put("selfAssessment", "OK");
            if (snapshot.path("valueSchema").isObject()) {
                body.put("values", sample(snapshot.path("valueSchema")));
            }
            String base = TIER1 + "/api/v1/approval-cases/" + caseId + "/submissions/draft/elements/" + code;
            send("PUT", base, token, body);
            if (snapshot.path("evidenceType").asString().startsWith("DOCUMENT")) {
                if (documentId == null) {
                    documentId = upload(token);
                }
                send("POST", base + "/documents", token, Map.of("documentId", documentId));
            }
        }
    }

    /** Minimal valid value for simple object schemas (required boolean/number/string properties). */
    private static Map<String, Object> sample(JsonNode schema) {
        var values = new LinkedHashMap<String, Object>();
        for (JsonNode required : schema.path("required")) {
            String name = required.asString();
            String type = schema.path("properties").path(name).path("type").asString();
            values.put(
                    name,
                    switch (type) {
                        case "boolean" -> Boolean.TRUE;
                        case "number", "integer" -> 1;
                        case "array" -> List.of();
                        default -> "live";
                    });
        }
        return values;
    }

    private String upload(String token) throws Exception {
        byte[] pdf = Files.readAllBytes(Path.of("src/test/resources/seed-fixture/documents/deckblatt.pdf"));
        String boundary = UUID.randomUUID().toString();
        byte[] head = ("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"deckblatt.pdf\""
                        + "\r\nContent-Type: application/pdf\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8);
        byte[] tail = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] body = new byte[head.length + pdf.length + tail.length];
        System.arraycopy(head, 0, body, 0, head.length);
        System.arraycopy(pdf, 0, body, head.length, pdf.length);
        System.arraycopy(tail, 0, body, head.length + pdf.length, tail.length);
        var request = HttpRequest.newBuilder(URI.create(TIER1 + "/api/v1/documents"))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        return check(http.send(request, HttpResponse.BodyHandlers.ofString()))
                .path("id")
                .asString();
    }

    private JsonNode awaitStatus(String name, long start, String app, String caseId, String token, String status)
            throws Exception {
        return await(name, start, () -> {
            JsonNode c = get(app + "/api/v1/approval-cases/" + caseId, token);
            return status.equals(c.path("status").asString()) ? c : null;
        });
    }

    private JsonNode await(String name, long start, Probe probe) throws Exception {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            JsonNode result = probe.get();
            if (result != null) {
                timings.put(name, (System.nanoTime() - start) / 1_000_000);
                return result;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("Timeout: " + name);
    }

    @FunctionalInterface
    interface Probe {
        JsonNode get() throws Exception;
    }

    private String token(String realm) throws Exception {
        String form = "grant_type=client_credentials&client_id=ppap-integration&client_secret=" + realm
                + "-integration-local-secret";
        var request = HttpRequest.newBuilder(
                        URI.create(KEYCLOAK + "/realms/" + realm + "/protocol/openid-connect/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        return check(http.send(request, HttpResponse.BodyHandlers.ofString()))
                .path("access_token")
                .asString();
    }

    private JsonNode get(String url, String token) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();
        return check(http.send(request, HttpResponse.BodyHandlers.ofString()));
    }

    private JsonNode send(String method, String url, String token, Object body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .method(
                        method,
                        body == null
                                ? HttpRequest.BodyPublishers.noBody()
                                : HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)))
                .build();
        return check(http.send(request, HttpResponse.BodyHandlers.ofString()));
    }

    private static JsonNode check(HttpResponse<String> response) {
        if (response.statusCode() >= 300) {
            throw new AssertionError(response.request().method() + " " + response.uri() + " → " + response.statusCode()
                    + " " + response.body());
        }
        return response.body().isBlank() ? JSON.createObjectNode() : JSON.readTree(response.body());
    }

    private static java.util.stream.Stream<JsonNode> stream(JsonNode array) {
        return java.util.stream.StreamSupport.stream(array.spliterator(), false);
    }
}
