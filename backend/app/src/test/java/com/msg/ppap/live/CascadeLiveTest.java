package com.msg.ppap.live;

import static com.msg.ppap.live.LiveClient.app;
import static com.msg.ppap.live.LiveClient.stream;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
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

/**
 * Phase 6 end-to-end (UC-09 cascade) against the running stack ({@code APP_DECISION_ALLOW_TECHNICAL_CLIENTS=true make
 * up PROFILE=tier2}): OEM requests from Tier-1 → Tier-1 creates a sub-supplier request to Tier-2 → Tier-2 submits →
 * Tier-1 approves → Tier-1 references the child in its own draft and submits to the OEM → the OEM receives the
 * submission with the reference (signature VALID) and can retrieve no Tier-2 document. Every message travels through
 * the EDCs of the participants.
 *
 * <p>Run with {@code ./mvnw -pl backend/app -am -Plive verify -Dtest=CascadeLiveTest}.
 */
@Tag("live")
class CascadeLiveTest {

    static final String TIER1_BPNL = System.getProperty("live.tier1Bpnl", "BPNL000000TIER01");
    static final String TIER2_BPNL = System.getProperty("live.tier2Bpnl", "BPNL000000TIER02");
    static final Duration TIMEOUT = Duration.ofSeconds(150);
    static final Path TIER2_PDF = Path.of("src/test/resources/seed-fixture/documents/bericht.pdf");
    /** Tier-1 uploads the same bytes itself (shared demo sample PDFs): allowed, only received records are blocked. */
    static final Path TIER1_PDF = TIER2_PDF;

    final LiveClient c = new LiveClient();

    @Test
    void cascadeOemTier1Tier2() throws Exception {
        String oem = c.clientToken("oem");
        String tier1 = c.clientToken("tier1");
        String tier2 = c.clientToken("tier2");
        long total = System.nanoTime();

        // 1. OEM → Tier-1
        String oemCase = createAndSend("oem", oem, TIER1_BPNL, "cascade parent " + UUID.randomUUID());
        String exchangeId = c.get(app("oem") + "/api/v1/approval-cases/" + oemCase, oem)
                .path("exchangeId")
                .asString();
        long t = System.nanoTime();
        String parent = awaitReceived("request reaches Tier-1", t, "tier1", tier1, exchangeId);
        c.send("POST", app("tier1") + "/api/v1/approval-cases/" + parent + "/start-work", tier1, null);

        // 2. Tier-1 creates the sub-supplier request (child) for an own sub-part mapped to Tier-2
        JsonNode mapping = mapping("tier1", tier1, TIER2_BPNL);
        JsonNode child = c.send(
                "POST",
                app("tier1") + "/api/v1/approval-cases/" + parent + "/sub-requests",
                tier1,
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
                        LocalDate.now().plusDays(20).toString(),
                        "remarks",
                        "cascade child"));
        String childId = child.path("id").asString();
        assertThat(child.path("parentCase").path("id").asString()).isEqualTo(parent);
        JsonNode parentDetail = c.get(app("tier1") + "/api/v1/approval-cases/" + parent, tier1);
        assertThat(LiveClient.texts(parentDetail.path("childCases"), "id")).contains(childId);
        assertThat(LiveClient.texts(parentDetail.path("draftCompleteness").path("hints"), "code"))
                .contains("SUB_SUPPLIER_PENDING");
        t = System.nanoTime();
        c.send("POST", app("tier1") + "/api/v1/approval-cases/" + childId + "/send", tier1, null);
        String childExchangeId = child.path("exchangeId").asString();

        // 3. Tier-2 submits
        String tier2Case = awaitReceived("sub-request reaches Tier-2", t, "tier2", tier2, childExchangeId);
        c.send("POST", app("tier2") + "/api/v1/approval-cases/" + tier2Case + "/start-work", tier2, null);
        String tier2Sha = fillDraft("tier2", tier2, tier2Case, TIER2_PDF);
        t = System.nanoTime();
        c.send(
                "POST",
                app("tier2") + "/api/v1/approval-cases/" + tier2Case + "/submissions/draft/submit",
                tier2,
                Map.of("confirmation", true));
        awaitStatus("Tier-2 submission reaches Tier-1", t, "tier1", tier1, childId, "SUBMITTED");

        // 4. Tier-1 (customer of Tier-2) approves
        c.send("POST", app("tier1") + "/api/v1/approval-cases/" + childId + "/start-review", tier1, null);
        JsonNode decision = c.send(
                "POST",
                app("tier1") + "/api/v1/approval-cases/" + childId + "/decision",
                tier1,
                Map.of("result", "APPROVED", "comment", "cascade live", "confirmation", true));
        String decisionHash = decision.path("payloadHash").asString();
        // the Tier-2 document is fetched into Tier-1's storage (needed for the leak check below)
        JsonNode fetched = c.await("Tier-2 document fetched by Tier-1", t, TIMEOUT, () -> {
            JsonNode rev = c.get(app("tier1") + "/api/v1/approval-cases/" + childId + "/submissions/1", tier1);
            return stream(rev.path("elements"))
                    .flatMap(e -> stream(e.path("documents")))
                    .filter(d -> tier2Sha.equals(d.path("sha256").asString()))
                    .filter(d -> "FETCHED".equals(d.path("transferStatus").asString()))
                    .findFirst()
                    .orElse(null);
        });

        // 5. Tier-1 references the child in its own draft (If-Match) and submits to the OEM
        JsonNode candidates =
                c.get(app("tier1") + "/api/v1/approval-cases/" + parent + "/sub-supplier-candidates", tier1);
        JsonNode candidate = stream(candidates)
                .filter(x -> childId.equals(x.path("caseId").asString()))
                .findFirst()
                .orElseThrow();
        assertThat(candidate.path("referenceable").asBoolean()).isTrue();
        assertThat(candidate.path("decisionHash").asString()).isEqualTo(decisionHash);
        long version = c.get(app("tier1") + "/api/v1/approval-cases/" + parent, tier1)
                .path("version")
                .asLong();
        JsonNode draft = c.send(
                "PUT",
                app("tier1") + "/api/v1/approval-cases/" + parent + "/submissions/draft/sub-supplier-references",
                tier1,
                Map.of("childCaseIds", List.of(childId)),
                Map.of("If-Match", "\"" + version + "\""));
        assertThat(draft.path("subSupplierReferences")
                        .get(0)
                        .path("decisionHash")
                        .asString())
                .isEqualTo(decisionHash);
        assertThat(LiveClient.texts(draft.path("completeness").path("hints"), "code"))
                .doesNotContain("SUB_SUPPLIER_PENDING");
        fillDraft("tier1", tier1, parent, TIER1_PDF);
        // AK-09-2: a document received from Tier-2 cannot be attached to the own submission
        String element = stream(c.get(app("tier1") + "/api/v1/approval-cases/" + parent, tier1)
                        .path("request")
                        .path("elements"))
                .filter(e -> e.path("snapshot").path("evidenceType").asString().startsWith("DOCUMENT"))
                .findFirst()
                .orElseThrow()
                .path("code")
                .asString();
        HttpResponse<String> leak = c.raw(
                "POST",
                app("tier1") + "/api/v1/approval-cases/" + parent + "/submissions/draft/elements/" + element
                        + "/documents",
                tier1,
                Map.of("documentId", fetched.path("id").asString()),
                Map.of());
        assertThat(leak.statusCode()).isEqualTo(422);
        assertThat(leak.body()).contains("SUB_SUPPLIER_DOCUMENT_NOT_SHAREABLE");
        t = System.nanoTime();
        c.send(
                "POST",
                app("tier1") + "/api/v1/approval-cases/" + parent + "/submissions/draft/submit",
                tier1,
                Map.of("confirmation", true));

        // 6. OEM receives the submission with the reference
        JsonNode submitted =
                awaitStatus("Tier-1 submission with reference reaches OEM", t, "oem", oem, oemCase, "SUBMITTED");
        assertThat(submitted
                        .path("submissions")
                        .get(0)
                        .path("signature")
                        .path("status")
                        .asString())
                .isEqualTo("VALID");
        JsonNode revision = c.get(app("oem") + "/api/v1/approval-cases/" + oemCase + "/submissions/1", oem);
        JsonNode reference = revision.path("subSupplierReferences").get(0);
        assertThat(reference.path("subSupplierBpnl").asString()).isEqualTo(TIER2_BPNL);
        assertThat(reference.path("exchangeId").asString()).isEqualTo(childExchangeId);
        assertThat(reference.path("decisionResult").asString()).isEqualTo("APPROVED");
        assertThat(reference.path("decisionHash").asString()).isEqualTo(decisionHash);
        assertThat(reference.path("childCaseId").isNull()).isTrue();

        // 7. provenance, not content: Tier-1's own upload with identical bytes reaches the OEM, the received Tier-2
        //    record is never served (no SERVED_TO_PARTNER on it), the signed payload carries no Tier-2 record
        HttpResponse<String> payload = c.raw(
                "GET",
                app("oem") + "/api/v1/approval-cases/" + oemCase + "/submissions/1/payload",
                oem,
                null,
                Map.of());
        assertThat(payload.statusCode()).isEqualTo(200);
        assertThat(payload.body()).contains(childExchangeId);
        c.await("own identical upload fetched by OEM", t, TIMEOUT, () -> {
            JsonNode rev = c.get(app("oem") + "/api/v1/approval-cases/" + oemCase + "/submissions/1", oem);
            return stream(rev.path("elements"))
                    .flatMap(e -> stream(e.path("documents")))
                    .filter(d -> tier2Sha.equals(d.path("sha256").asString()))
                    .filter(d -> "FETCHED".equals(d.path("transferStatus").asString()))
                    .findFirst()
                    .orElse(null);
        });
        String ownRecord = stream(c.get(app("tier1") + "/api/v1/approval-cases/" + parent + "/submissions/1", tier1)
                        .path("elements"))
                .flatMap(e -> stream(e.path("documents")))
                .filter(d -> tier2Sha.equals(d.path("sha256").asString()))
                .findFirst()
                .orElseThrow()
                .path("id")
                .asString();
        assertThat(ownRecord).isNotEqualTo(fetched.path("id").asString());
        c.await("own record served to OEM (audit)", t, TIMEOUT, () -> {
            JsonNode events =
                    c.get(app("tier1") + "/api/v1/audit-events?objectType=DOCUMENT&objectId=" + ownRecord, tier1);
            return events.path("content").toString().contains("SERVED_TO_PARTNER") ? events : null;
        });
        assertThat(c.get(
                                app("tier1") + "/api/v1/audit-events?objectType=DOCUMENT&objectId="
                                        + fetched.path("id").asString(),
                                tier1)
                        .path("content")
                        .toString())
                .doesNotContain("SERVED_TO_PARTNER");

        // 8. audit export of the parent at Tier-1 is signed and lists the reference
        HttpResponse<String> export = c.raw(
                "GET", app("tier1") + "/api/v1/approval-cases/" + parent + "/audit-export", tier1, null, Map.of());
        assertThat(export.statusCode()).isEqualTo(200);
        assertThat(export.headers().firstValue("X-Signature")).isPresent();
        assertThat(export.body()).contains(decisionHash);

        c.timings.put("total", (System.nanoTime() - total) / 1_000_000);
        System.out.println("CASCADE LIVE TIMINGS (ms): " + c.timings);
    }

    private String createAndSend(String realm, String token, String supplierBpnl, String remarks) throws Exception {
        JsonNode mapping = mapping(realm, token, supplierBpnl);
        JsonNode created = c.send(
                "POST",
                app(realm) + "/api/v1/approval-cases",
                token,
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
                        remarks));
        String id = created.path("id").asString();
        c.send("POST", app(realm) + "/api/v1/approval-cases/" + id + "/send", token, null);
        return id;
    }

    private JsonNode mapping(String realm, String token, String supplierBpnl) throws Exception {
        return stream(c.get(app(realm) + "/api/v1/part-mappings?partnerRole=SUPPLIER&size=200", token)
                        .path("content"))
                .filter(m -> supplierBpnl.equals(m.path("partner").path("bpnl").asString()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no part mapping to " + supplierBpnl + " at " + realm));
    }

    private String awaitReceived(String name, long start, String realm, String token, String exchangeId)
            throws Exception {
        return c.await(
                        name,
                        start,
                        TIMEOUT,
                        () -> stream(c.get(app(realm) + "/api/v1/approval-cases?size=200&ownRole=SUPPLIER", token)
                                        .path("content"))
                                .filter(x ->
                                        exchangeId.equals(x.path("exchangeId").asString()))
                                .findFirst()
                                .orElse(null))
                .path("id")
                .asString();
    }

    private JsonNode awaitStatus(String name, long start, String realm, String token, String caseId, String status)
            throws Exception {
        return c.await(name, start, TIMEOUT, () -> {
            JsonNode x = c.get(app(realm) + "/api/v1/approval-cases/" + caseId, token);
            return status.equals(x.path("status").asString()) ? x : null;
        });
    }

    /** Fills all required elements of the draft; returns the SHA-256 of the uploaded evidence document. */
    private String fillDraft(String realm, String token, String caseId, Path pdf) throws Exception {
        JsonNode detail = c.get(app(realm) + "/api/v1/approval-cases/" + caseId, token);
        JsonNode document = null;
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
            String base = app(realm) + "/api/v1/approval-cases/" + caseId + "/submissions/draft/elements/" + code;
            c.send("PUT", base, token, body);
            if (snapshot.path("evidenceType").asString().startsWith("DOCUMENT")) {
                if (document == null) {
                    document = c.upload(app(realm), token, pdf);
                }
                c.send(
                        "POST",
                        base + "/documents",
                        token,
                        Map.of("documentId", document.path("id").asString()));
            }
        }
        return document == null ? null : document.path("sha256").asString();
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
}
