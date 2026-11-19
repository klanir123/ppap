package com.msg.ppap.live;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Phase 5 end-to-end against the running stack ({@code make up PROFILE=tier2}): OEM (customer) ⇄ Tier-1 (supplier)
 * in all three exchange modes – PUSH, PUSH_NOTIFY_PULL_PAYLOAD, PULL – each with cold EDRs (all EDRs of both EDCs
 * deleted) and warm EDRs. Per run: request with attachment (customer → supplier document pull), submission with
 * evidence (supplier → customer document pull), SHA-256 of the fetched content compared with the original, effective
 * exchange mode per revision, twin in the supplier's DTR. Prints timings and the number of EDC interactions (input of
 * ADR-0002 „Messwerte Phase 5“).
 *
 * <p>Run with {@code ./mvnw -pl backend/app -am -Plive verify -Dtest=PullModesLiveTest}. For mode PULL the stack
 * should poll fast: {@code APP_PULL_POLL_INTERVAL=10s make up PROFILE=tier2}.
 */
@Tag("live")
class PullModesLiveTest {

    static final JsonMapper JSON = JsonMapper.builder().build();
    static final String KEYCLOAK = System.getProperty("live.keycloak", "http://localhost:8180");
    static final String OEM = System.getProperty("live.oem", "http://localhost:8081");
    static final String TIER1 = System.getProperty("live.tier1", "http://localhost:8082");
    static final String OEM_MGMT = System.getProperty("live.oemManagement", "http://localhost:8191/management");
    static final String TIER1_MGMT = System.getProperty("live.tier1Management", "http://localhost:8291/management");
    static final String EDC_KEY = System.getProperty("live.edcApiKey", "local-management-key");
    static final String OEM_BPNL = System.getProperty("live.oemBpnl", "BPNL00000000OEM1");
    static final String TIER1_BPNL = System.getProperty("live.tier1Bpnl", "BPNL000000TIER01");
    static final Duration TIMEOUT = Duration.ofSeconds(150);
    static final Path DOCS = Path.of("src/test/resources/seed-fixture/documents");
    static final List<String> EDC_STEPS = List.of("CATALOG", "NEGOTIATION", "TRANSFER", "EDR");
    static final List<String> DATA_PLANE_STEPS =
            List.of("DELIVERY", "DTR_LOOKUP", "SHELL_DESCRIPTOR", "SUBMODEL_FETCH", "DOCUMENT_FETCH", "KEY_FETCH");

    final HttpClient http = HttpClient.newBuilder()
            .proxy(HttpClient.Builder.NO_PROXY)
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    final List<String> report = new ArrayList<>();

    @Test
    void allModesColdAndWarm() throws Exception {
        String oem = token("oem");
        String tier1 = token("tier1");
        JsonNode relation = stream(
                        get(TIER1 + "/api/v1/relations?size=200", tier1).get("content"))
                .filter(r -> OEM_BPNL.equals(r.path("partnerBpnl").asString())
                        && "CUSTOMER".equals(r.path("partnerRole").asString()))
                .findFirst()
                .orElseThrow();
        String originalMode = relation.path("exchangeMode").asString();
        try {
            for (String mode : List.of("PUSH", "PUSH_NOTIFY_PULL_PAYLOAD", "PULL")) {
                setMode(relation, mode, tier1);
                clearEdrs(OEM_MGMT);
                clearEdrs(TIER1_MGMT);
                run(mode, "cold", oem, tier1);
                run(mode, "warm", oem, tier1);
            }
        } finally {
            setMode(relation, originalMode, tier1);
            System.out.println("PHASE 5 LIVE MEASUREMENTS");
            report.forEach(System.out::println);
        }
    }

    private void run(String mode, String edr, String oem, String tier1) throws Exception {
        // OEM: request with attachment
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
                        "phase 5 live " + mode + " " + edr + " " + UUID.randomUUID()));
        String oemCase = created.path("id").asString();
        String exchangeId = created.path("exchangeId").asString();
        byte[] attachment = Files.readAllBytes(DOCS.resolve("bericht.pdf"));
        String attachmentId = upload(OEM, oem, "bericht.pdf", attachment);
        send(
                "POST",
                OEM + "/api/v1/approval-cases/" + oemCase + "/request/attachments",
                oem,
                Map.of("documentId", attachmentId));
        long t0 = System.nanoTime();
        send("POST", OEM + "/api/v1/approval-cases/" + oemCase + "/send", oem, null);

        // Tier-1: case + attachment fetched from the OEM and verified
        String tier1Case = await(() -> stream(get(TIER1 + "/api/v1/approval-cases?size=200&ownRole=SUPPLIER", tier1)
                                .get("content"))
                        .filter(c -> exchangeId.equals(c.path("exchangeId").asString()))
                        .findFirst()
                        .orElse(null))
                .path("id")
                .asString();
        JsonNode fetchedAttachment = await(() -> {
            JsonNode a = get(TIER1 + "/api/v1/approval-cases/" + tier1Case, tier1)
                    .path("request")
                    .path("attachments")
                    .path(0);
            return "FETCHED".equals(a.path("transferStatus").asString()) ? a : null;
        });
        long attachmentMs = ms(t0);
        assertThat(fetchedAttachment.path("hashVerified").asBoolean()).isTrue();
        assertThat(sha256(content(TIER1, tier1, fetchedAttachment.path("id").asString())))
                .isEqualTo(sha256(attachment));

        // Tier-1 works and submits with evidence
        send("POST", TIER1 + "/api/v1/approval-cases/" + tier1Case + "/start-work", tier1, null);
        byte[] evidence = Files.readAllBytes(DOCS.resolve("deckblatt.pdf"));
        fillDraft(tier1Case, tier1, evidence);
        long t1 = System.nanoTime();
        send(
                "POST",
                TIER1 + "/api/v1/approval-cases/" + tier1Case + "/submissions/draft/submit",
                tier1,
                Map.of("confirmation", true));
        JsonNode submitted = await(() -> {
            JsonNode c = get(OEM + "/api/v1/approval-cases/" + oemCase, oem);
            return "SUBMITTED".equals(c.path("status").asString()) ? c : null;
        });
        long submissionMs = ms(t1);
        JsonNode revision = submitted.path("submissions").get(0);
        assertThat(revision.path("signature").path("status").asString()).isEqualTo("VALID");
        assertThat(revision.path("exchangeMode").asString()).isEqualTo(mode);
        JsonNode supplierRevision = get(TIER1 + "/api/v1/approval-cases/" + tier1Case, tier1)
                .path("submissions")
                .get(0);
        assertThat(supplierRevision.path("exchangeMode").asString()).isEqualTo(mode);

        // OEM: evidence fetched from Tier-1 and verified
        JsonNode evidenceRef = await(() -> {
            JsonNode s = get(OEM + "/api/v1/approval-cases/" + oemCase + "/submissions/1", oem);
            for (JsonNode element : s.path("elements")) {
                for (JsonNode d : element.path("documents")) {
                    if ("FETCHED".equals(d.path("transferStatus").asString())) {
                        return d;
                    }
                }
            }
            return null;
        });
        long evidenceMs = ms(t1);
        assertThat(evidenceRef.path("hashVerified").asBoolean()).isTrue();
        assertThat(sha256(content(OEM, oem, evidenceRef.path("id").asString()))).isEqualTo(sha256(evidence));

        // twin in the supplier's DTR (not for PUSH)
        int twin = status(TIER1 + "/api/v1/twins/" + exchangeId, token("tier1"));
        assertThat(twin).isEqualTo("PUSH".equals(mode) ? 404 : 200);

        report.add(String.format(
                "%-26s %-4s | request+attachment %5d ms | submit→SUBMITTED %5d ms | submit→evidence %5d ms",
                mode, edr, attachmentMs, submissionMs, evidenceMs));
        describe("  OEM", get(OEM + "/api/v1/approval-cases/" + oemCase + "/exchange", oem));
        describe("  T1 ", get(TIER1 + "/api/v1/approval-cases/" + tier1Case + "/exchange", tier1));
    }

    /** One line per record: type, status, EDC interactions (non-skipped), data plane calls and step durations. */
    private void describe(String side, JsonNode records) {
        for (JsonNode r : records) {
            List<JsonNode> steps = stream(r.path("steps")).toList();
            long edc = steps.stream()
                    .filter(s -> EDC_STEPS.contains(s.path("step").asString())
                            && "OK".equals(s.path("status").asString())
                            && !s.path("details").path("cachedEdr").asBoolean(false))
                    .count();
            long dataPlane = steps.stream()
                    .filter(s -> DATA_PLANE_STEPS.contains(s.path("step").asString()))
                    .count();
            report.add(String.format(
                    "%s %-8s %-25s %-9s edc=%d dp=%d %s",
                    side,
                    r.path("direction").asString(),
                    r.path("messageType").asString(),
                    r.path("status").asString(),
                    edc,
                    dataPlane,
                    steps.stream()
                            .map(s -> s.path("target").asString() + "/"
                                    + s.path("step").asString() + ":"
                                    + s.path("status").asString().charAt(0) + ":"
                                    + s.path("durationMs").asLong())
                            .toList()));
        }
    }

    private void setMode(JsonNode relation, String mode, String token) throws Exception {
        JsonNode current =
                get(TIER1 + "/api/v1/relations/" + relation.path("id").asString(), token);
        var body = new LinkedHashMap<String, Object>();
        body.put("partnerBpnl", current.path("partnerBpnl").asString());
        body.put("partnerRole", current.path("partnerRole").asString());
        body.put("exchangeMode", mode);
        body.put("policyProfile", current.path("policyProfile").asString("default"));
        body.put("active", true);
        send("PUT", TIER1 + "/api/v1/relations/" + relation.path("id").asString(), token, body);
    }

    /** Deletes all stored EDRs of a connector (cold run). */
    private void clearEdrs(String management) throws Exception {
        var query = HttpRequest.newBuilder(URI.create(management + "/v3/edrs/request"))
                .header("X-Api-Key", EDC_KEY)
                .header("Content-Type", "application/json")
                .POST(
                        HttpRequest.BodyPublishers.ofString(
                                "{\"@context\":{\"@vocab\":\"https://w3id.org/edc/v0.0.1/ns/\"},\"@type\":\"QuerySpec\",\"limit\":1000}"))
                .build();
        JsonNode entries = JSON.readTree(
                http.send(query, HttpResponse.BodyHandlers.ofString()).body());
        for (JsonNode e : entries) {
            String id = e.path("transferProcessId").asString(e.path("@id").asString());
            http.send(
                    HttpRequest.newBuilder(URI.create(management + "/v3/edrs/" + id))
                            .header("X-Api-Key", EDC_KEY)
                            .DELETE()
                            .build(),
                    HttpResponse.BodyHandlers.discarding());
        }
    }

    private void fillDraft(String caseId, String token, byte[] evidence) throws Exception {
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
                    documentId = upload(TIER1, token, "deckblatt.pdf", evidence);
                }
                send("POST", base + "/documents", token, Map.of("documentId", documentId));
            }
        }
    }

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

    private String upload(String app, String token, String name, byte[] content) throws Exception {
        String boundary = UUID.randomUUID().toString();
        byte[] head = ("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + name
                        + "\"\r\nContent-Type: application/pdf\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8);
        byte[] tail = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] body = new byte[head.length + content.length + tail.length];
        System.arraycopy(head, 0, body, 0, head.length);
        System.arraycopy(content, 0, body, head.length, content.length);
        System.arraycopy(tail, 0, body, head.length + content.length, tail.length);
        var request = HttpRequest.newBuilder(URI.create(app + "/api/v1/documents"))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        return check(http.send(request, HttpResponse.BodyHandlers.ofString()))
                .path("id")
                .asString();
    }

    private byte[] content(String app, String token, String documentId) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(app + "/api/v1/documents/" + documentId + "/content"))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();
        HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        assertThat(response.statusCode()).isEqualTo(200);
        return response.body();
    }

    private static String sha256(byte[] content) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }

    private JsonNode await(Probe probe) throws Exception {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            JsonNode result = probe.get();
            if (result != null) {
                return result;
            }
            Thread.sleep(150);
        }
        throw new AssertionError("Timeout");
    }

    @FunctionalInterface
    interface Probe {
        JsonNode get() throws Exception;
    }

    private static long ms(long start) {
        return (System.nanoTime() - start) / 1_000_000;
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

    private int status(String url, String token) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();
        return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
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

    private static Stream<JsonNode> stream(JsonNode array) {
        return StreamSupport.stream(array.spliterator(), false);
    }
}
