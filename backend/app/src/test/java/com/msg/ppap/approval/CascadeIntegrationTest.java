package com.msg.ppap.approval;

import static com.msg.ppap.approval.ApprovalScenario.engineer;
import static com.msg.ppap.approval.ApprovalScenario.supplier;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.msg.ppap.approval.application.port.in.SignatureVerificationUseCase;
import com.msg.ppap.exchange.application.port.out.PartnerKeyStore;
import com.msg.ppap.identity.PayloadSigner;
import com.msg.ppap.identity.PublicKeys;
import com.msg.ppap.identity.SignatureVerifier;
import com.msg.ppap.semantics.api.DocumentReference;
import com.msg.ppap.semantics.api.EncodedPayload;
import com.msg.ppap.semantics.api.ExchangeCodec;
import com.msg.ppap.semantics.api.Notification;
import com.msg.ppap.semantics.api.Notification.MessageType;
import com.msg.ppap.semantics.api.NotificationCodec;
import com.msg.ppap.semantics.api.RequestMessage;
import com.msg.ppap.semantics.api.SubmissionMessage;
import com.msg.ppap.support.AbstractIntegrationTest;
import com.msg.ppap.support.TestJwts;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Phase 6 with Testcontainers and a stubbed exchange (partner messages are injected through the receiver endpoint,
 * outbound messages stay in the outbox): cascade end-to-end within one tenant that is supplier of a customer and
 * customer of a supplier (UC-09), sub-supplier documents never shared, served-to-partner audit, signed audit export,
 * usage counting vs. recomputation, activity feed with partner names, signature re-verification, assign-part with
 * replacement of an existing mapping.
 */
@TestPropertySource(
        properties = {"ppap.edc.callback-secret=test-callback-secret", "ppap.exchange.worker-interval=200ms"})
class CascadeIntegrationTest extends AbstractIntegrationTest {

    static final String CASES = "/api/v1/approval-cases";
    static final String SECRET_HEADER = "X-Ppap-Edc-Secret";
    static final String SECRET = "test-callback-secret";

    /** Tier-1 user: supplier towards the customer and customer towards the sub-supplier. */
    static JwtRequestPostProcessor tier1() {
        return TestJwts.user("tier1.quality", "SUPPLIER_QUALITY_MANAGER", "CUSTOMER_QUALITY_ENGINEER");
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    JsonMapper json;

    @Autowired
    ExchangeCodec codec;

    @Autowired
    NotificationCodec notifications;

    @Autowired
    PayloadSigner signer;

    @Autowired
    PublicKeys publicKeys;

    @Autowired
    PartnerKeyStore partnerKeys;

    @Autowired
    SignatureVerifier verifier;

    @Autowired
    SignatureVerificationUseCase verification;

    @Autowired
    com.msg.ppap.documents.api.Documents documents;

    ApprovalScenario s;

    @BeforeEach
    void setUp() throws Exception {
        s = new ApprovalScenario(mvc, json).create();
    }

    @Test
    void cascadeFromSubRequestToTheSignedReference() throws Exception {
        String parent = receivedParent();
        JsonNode child = subRequest(parent, tier1(), 201);
        String childId = child.get("id").asString();
        assertThat(child.get("parentCase").get("id").asString()).isEqualTo(parent);
        assertThat(child.get("ownRole").asString()).isEqualTo("CUSTOMER");
        subRequest(parent, supplier(), 403);

        JsonNode parentDetail = detail(parent);
        assertThat(parentDetail.get("childCases").get(0).get("id").asString()).isEqualTo(childId);
        assertThat(parentDetail.get("allowedActions").toString()).contains("REQUEST_SUB_SUPPLIER");
        assertThat(parentDetail
                        .get("draftCompleteness")
                        .get("hints")
                        .get(0)
                        .get("code")
                        .asString())
                .isEqualTo("SUB_SUPPLIER_PENDING");
        mvc.perform(get(CASES + "/stats").with(engineer()))
                .andExpect(jsonPath("$.blockingChildren").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

        // not decided yet → not referenceable
        mvc.perform(put(CASES + "/" + parent + "/submissions/draft/sub-supplier-references")
                        .with(supplier())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("childCaseIds", List.of(childId)))))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("SUB_SUPPLIER_REFERENCE_INVALID"));

        String decisionHash = approveChild(childId, child.get("exchangeId").asString(), pdfSha("sub-doc"));
        JsonNode candidates = read(mvc.perform(
                        get(CASES + "/" + parent + "/sub-supplier-candidates").with(supplier()))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(candidates.get(0).get("referenceable").asBoolean()).isTrue();
        assertThat(candidates.get(0).get("decisionHash").asString()).isEqualTo(decisionHash);

        long version = detail(parent).get("version").asLong();
        mvc.perform(put(CASES + "/" + parent + "/submissions/draft/sub-supplier-references")
                        .with(supplier())
                        .header("If-Match", "\"" + (version + 3) + "\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("childCaseIds", List.of(childId)))))
                .andExpect(status().isPreconditionFailed());
        mvc.perform(put(CASES + "/" + parent + "/submissions/draft/sub-supplier-references")
                        .with(supplier())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("childCaseIds", List.of(UUID.randomUUID())))))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("childCaseIds[0]"));
        JsonNode draft = read(mvc.perform(put(CASES + "/" + parent + "/submissions/draft/sub-supplier-references")
                        .with(supplier())
                        .header("If-Match", "\"" + version + "\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("childCaseIds", List.of(childId, childId)))))
                .andExpect(status().isOk())
                .andExpect(header().exists("ETag"))
                .andReturn());
        assertThat(draft.get("subSupplierReferences")).hasSize(1);
        assertThat(draft.get("subSupplierReferences").get(0).get("childCaseId").asString())
                .isEqualTo(childId);
        assertThat(draft.get("completeness").get("hints")).isEmpty();

        // AK-09-2 by provenance: the document record received from the sub-supplier (stored by the document transfer)
        // cannot be attached to the own submission …
        String receivedId = documents
                .importVerified(
                        "sub-doc.pdf",
                        new java.io.ByteArrayInputStream(ApprovalScenario.pdf("sub-doc")),
                        pdfSha("sub-doc"),
                        "partner:" + s.supplierBpnl)
                .id()
                .toString();
        mvc.perform(post(CASES + "/" + parent + "/submissions/draft/elements/" + s.docElement + "/documents")
                        .with(supplier())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("documentId", receivedId))))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("SUB_SUPPLIER_DOCUMENT_NOT_SHAREABLE"));
        // … and is never served to the customer of the parent
        mvc.perform(get("/internal/edc/documents/" + pdfSha("sub-doc"))
                        .header(SECRET_HEADER, SECRET)
                        .header("Edc-Bpn", s.customerBpnl))
                .andExpect(status().isNotFound());
        // an own upload with identical bytes is a conscious act of the user: attachable and served (as own record)
        String ownDocumentId = s.uploadPdf("sub-doc");
        assertThat(ownDocumentId).isNotEqualTo(receivedId);
        fillAndSubmitParent(parent, ownDocumentId);
        JsonNode revision =
                read(mvc.perform(get(CASES + "/" + parent + "/submissions/1").with(supplier()))
                        .andReturn());
        assertThat(revision.get("subSupplierReferences")
                        .get(0)
                        .get("decisionHash")
                        .asString())
                .isEqualTo(decisionHash);
        MvcResult payload = mvc.perform(
                        get(CASES + "/" + parent + "/submissions/1/payload").with(supplier()))
                .andExpect(status().isOk())
                .andReturn();
        String canonical = payload.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(canonical)
                .contains(decisionHash)
                .contains(child.get("exchangeId").asString());
        SubmissionMessage decoded = codec.decodeSubmission(canonical.getBytes(StandardCharsets.UTF_8))
                .message();
        assertThat(decoded.subSupplierReferences()).singleElement().satisfies(r -> {
            assertThat(r.subSupplierBpnl()).isEqualTo(s.supplierBpnl);
            assertThat(r.decisionResult()).isEqualTo("APPROVED");
        });

        // served-to-partner audit for the own evidence document
        String ownSha = read(mvc.perform(
                                get("/api/v1/documents/" + ownDocumentId).with(supplier()))
                        .andReturn())
                .get("sha256")
                .asString();
        mvc.perform(get("/internal/edc/documents/" + ownSha)
                        .header(SECRET_HEADER, SECRET)
                        .header("Edc-Bpn", s.customerBpnl)
                        .header("Edc-Contract-Agreement-Id", "agreement-1"))
                .andExpect(status().isOk());
        mvc.perform(get("/internal/edc/jwks").header(SECRET_HEADER, SECRET).header("Edc-Bpn", s.customerBpnl))
                .andExpect(status().isOk());
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> mvc.perform(
                                get("/api/v1/audit-events?objectType=DOCUMENT&objectId=" + ownDocumentId)
                                        .with(engineer()))
                        .andExpect(jsonPath("$.content[0].action").value("SERVED_TO_PARTNER"))
                        .andExpect(jsonPath("$.content[0].actor").value("partner:" + s.customerBpnl))
                        .andExpect(jsonPath("$.content[0].details.agreementId").value("agreement-1")));
        // the served record is the own upload with identical bytes, never the received Tier-2 record
        assertThat(ownSha).isEqualTo(pdfSha("sub-doc"));
        mvc.perform(get("/api/v1/audit-events?objectType=DOCUMENT&objectId=" + receivedId)
                        .with(engineer()))
                .andExpect(jsonPath("$.content[*].action")
                        .value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("SERVED_TO_PARTNER"))));
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> mvc.perform(
                                get("/api/v1/audit-events?objectType=PUBLIC_KEYS&objectId=" + ApprovalScenario.TENANT)
                                        .with(engineer()))
                        .andExpect(jsonPath("$.content[0].action").value("SERVED_TO_PARTNER")));

        // signed audit export of the parent
        MvcResult export = mvc.perform(
                        get(CASES + "/" + parent + "/audit-export").with(engineer()))
                .andExpect(status().isOk())
                .andExpect(header().string(
                                "Content-Disposition",
                                org.hamcrest.Matchers.containsString("audit-"
                                        + detail(parent).get("caseNumber").asString() + ".json")))
                .andReturn();
        byte[] body = export.getResponse().getContentAsByteArray();
        assertThat(verifier.verify(body, export.getResponse().getHeader("X-Signature")))
                .isEqualTo(SignatureVerifier.Result.VALID);
        assertThat(export.getResponse().getHeader("X-Payload-Hash")).isEqualTo("sha256:" + sha(body));
        JsonNode exported = json.readTree(body);
        assertThat(exported.get("auditChainValid").asBoolean()).isTrue();
        assertThat(exported.get("auditEvents").size()).isGreaterThan(3);
        assertThat(exported.get("submissions")
                        .get(0)
                        .get("subSupplierReferences")
                        .get(0)
                        .get("decisionHash")
                        .asString())
                .isEqualTo(decisionHash);
        assertThat(exported.get("timeline").get(0).get("actorDisplay").asString())
                .isEqualTo("Customer " + s.customerBpnl);
        assertThat(exported.get("exportedBy").asString()).isEqualTo("oem.quality");
        mvc.perform(get(CASES + "/" + UUID.randomUUID() + "/audit-export").with(engineer()))
                .andExpect(status().isNotFound());

        // dataspace animation: recent item id == messageId of the exchange record (same id as in the SSE events)
        JsonNode records =
                read(mvc.perform(get(CASES + "/" + childId + "/exchange").with(engineer()))
                        .andReturn());
        JsonNode recent =
                read(mvc.perform(get("/api/v1/dataspace/recent?limit=200").with(engineer()))
                        .andExpect(status().isOk())
                        .andReturn());
        assertThat(LiveStream.of(recent).map(r -> r.get("id").asString()))
                .contains(records.get(0).get("messageId").asString());

        // child knows its parent, activity feed shows the partner name
        assertThat(detail(childId).get("parentCase").get("caseNumber").asString())
                .isEqualTo(detail(parent).get("caseNumber").asString());
        JsonNode activity = read(mvc.perform(get("/api/v1/activity?limit=200").with(engineer()))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(LiveStream.of(activity)
                        .filter(a -> a.get("caseId").asString().equals(parent))
                        .filter(a -> a.get("actor").asString().equals("partner:" + s.customerBpnl))
                        .map(a -> a.get("actorDisplay").asString()))
                .contains("Customer " + s.customerBpnl);
        assertThat(detail(parent).get("timeline").get(0).get("actorDisplay").asString())
                .isEqualTo("Customer " + s.customerBpnl);
    }

    @Test
    void usageCountersEqualTheRecomputation() throws Exception {
        mvc.perform(post("/api/v1/usage/recompute").with(TestJwts.admin())).andExpect(status().isOk());
        String parent = receivedParent();
        JsonNode child = subRequest(parent, tier1(), 201);
        approveChild(child.get("id").asString(), child.get("exchangeId").asString(), pdfSha("usage-" + parent));
        fillAndSubmitParent(parent, s.uploadPdf("usage-own-" + parent));
        String month = LocalDate.now(java.time.ZoneOffset.UTC).toString().substring(0, 7);
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            JsonNode usage = read(mvc.perform(
                            get("/api/v1/usage?from=" + month + "&to=" + month).with(TestJwts.admin()))
                    .andExpect(status().isOk())
                    .andReturn());
            assertThat(usage.get(0)
                            .get("details")
                            .get("APPROVAL_CASES_SUBMITTED")
                            .asLong())
                    .isGreaterThanOrEqualTo(1);
            assertThat(usage.get(0)
                            .get("details")
                            .get("APPROVAL_CASES_RECEIVED")
                            .asLong())
                    .isGreaterThanOrEqualTo(1);
        });
        // every revision is counted exactly once by the events: the recomputation changes nothing
        mvc.perform(post("/api/v1/usage/recompute").with(TestJwts.admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consistent").value(true))
                .andExpect(jsonPath("$.differences").isEmpty());
        MvcResult csv = mvc.perform(get("/api/v1/usage/export.csv").with(TestJwts.admin()))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(csv.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .startsWith("month;approvalCases;revisions;")
                .contains(month + ";");
        mvc.perform(get("/api/v1/usage").with(engineer())).andExpect(status().isForbidden());
    }

    @Test
    void unverifiedSignaturesAreVerifiedAgainOnceTheKeyIsKnown() throws Exception {
        JsonNode child = subRequest(receivedParent(), tier1(), 201);
        String childId = child.get("id").asString();
        mvc.perform(post(CASES + "/" + childId + "/send").with(engineer())).andExpect(status().isOk());
        EncodedPayload payload = codec.encodeSubmission(
                submission(UUID.fromString(child.get("exchangeId").asString()), pdfSha("unverified-" + childId)));
        String jws = signer.sign(payload.canonical()).jws();
        mvc.perform(receive(envelope(payload, child.get("exchangeId").asString(), jws)))
                .andExpect(status().isAccepted());
        awaitStatus(childId, "SUBMITTED");
        mvc.perform(get(CASES + "/" + childId + "/submissions/1").with(engineer()))
                .andExpect(jsonPath("$.signature.status").value("UNVERIFIED"));
        saveSupplierKey(signer.sign(payload.canonical()).kid());
        assertThat(verification.reverifyUnverified(s.supplierBpnl)).isGreaterThanOrEqualTo(1);
        mvc.perform(post(CASES + "/" + childId + "/submissions/1/verify-signature")
                        .with(engineer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signature.status").value("VALID"));
    }

    @Test
    void assignPartCanReplaceAnExistingMapping() throws Exception {
        UUID exchangeId = UUID.randomUUID();
        String unknownNumber = "NEW-" + exchangeId.toString().substring(0, 8);
        mvc.perform(receive(requestEnvelope(exchangeId, unknownNumber))).andExpect(status().isAccepted());
        String caseId = awaitSupplierCase(exchangeId).get("id").asString();
        var body = Map.of("partId", s.supplierPartId, "saveMapping", true);
        mvc.perform(post(CASES + "/" + caseId + "/assign-part")
                        .with(supplier())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_KEY"));
        mvc.perform(post(CASES + "/" + caseId + "/assign-part")
                        .with(supplier())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "partId", s.supplierPartId, "saveMapping", true, "replaceExistingMapping", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clarification").doesNotExist());
        mvc.perform(get("/api/v1/relations/" + s.customerRelationId + "/part-mappings")
                        .with(TestJwts.manager()))
                .andExpect(jsonPath("$.content[0].partnerPartNumber").value(unknownNumber));
    }

    // ---------------------------------------------------------------------------------------------------------

    /** A request of the customer received via the dataspace and started (supplier case IN_PROGRESS). */
    String receivedParent() throws Exception {
        UUID exchangeId = UUID.randomUUID();
        mvc.perform(receive(requestEnvelope(exchangeId, s.customerNumberAtSupplier)))
                .andExpect(status().isAccepted());
        String id = awaitSupplierCase(exchangeId).get("id").asString();
        mvc.perform(post(CASES + "/" + id + "/start-work").with(supplier())).andExpect(status().isOk());
        return id;
    }

    JsonNode subRequest(String parent, JwtRequestPostProcessor user, int expected) throws Exception {
        MvcResult result = mvc.perform(post(CASES + "/" + parent + "/sub-requests")
                        .with(user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "partId", s.customerPartId,
                                "relationId", s.supplierRelationId,
                                "reasonCode", s.reason,
                                "levelCode", s.level,
                                "dueDate", LocalDate.now().plusDays(20).toString()))))
                .andExpect(status().is(expected))
                .andReturn();
        return read(result);
    }

    /** Sends the child, injects the signed submission of the sub-supplier and approves it; returns the decision hash. */
    String approveChild(String childId, String childExchangeId, String documentSha) throws Exception {
        mvc.perform(post(CASES + "/" + childId + "/send").with(engineer())).andExpect(status().isOk());
        EncodedPayload payload = codec.encodeSubmission(submission(UUID.fromString(childExchangeId), documentSha));
        var signature = signer.sign(payload.canonical());
        saveSupplierKey(signature.kid());
        mvc.perform(receive(envelope(payload, childExchangeId, signature.jws())))
                .andExpect(status().isAccepted());
        awaitStatus(childId, "SUBMITTED");
        mvc.perform(get(CASES + "/" + childId + "/submissions/1").with(engineer()))
                .andExpect(jsonPath("$.signature.status").value("VALID"));
        mvc.perform(post(CASES + "/" + childId + "/start-review").with(engineer()))
                .andExpect(status().isOk());
        JsonNode decision = read(mvc.perform(post(CASES + "/" + childId + "/decision")
                        .with(engineer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("result", "APPROVED", "confirmation", true))))
                .andExpect(status().isOk())
                .andReturn());
        return decision.get("payloadHash").asString();
    }

    void fillAndSubmitParent(String parent, String documentId) throws Exception {
        mvc.perform(put(CASES + "/" + parent + "/submissions/draft/elements/" + s.docElement)
                        .with(supplier())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("selfAssessment", "OK"))))
                .andExpect(status().isOk());
        mvc.perform(post(CASES + "/" + parent + "/submissions/draft/elements/" + s.docElement + "/documents")
                        .with(supplier())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("documentId", documentId))))
                .andExpect(status().isOk());
        mvc.perform(post(CASES + "/" + parent + "/submissions/draft/submit")
                        .with(supplier())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmation\":true}"))
                .andExpect(status().isOk());
    }

    SubmissionMessage submission(UUID exchangeId, String documentSha) {
        return new SubmissionMessage(
                exchangeId,
                "PPF-2026-55555",
                1,
                ApprovalScenario.TENANT,
                s.supplierBpnl,
                s.customerPartNumber,
                s.supplierPartNumberAtCustomer,
                s.reason,
                s.level,
                List.of(
                        new SubmissionMessage.Element(
                                s.docElement,
                                "REQUIRED",
                                "OK",
                                null,
                                null,
                                List.of(new DocumentReference("sub-doc.pdf", "application/pdf", 100, documentSha))),
                        new SubmissionMessage.Element(
                                s.valueElement, "REQUIRED", "OK", Map.of("quantity", 2), null, List.of())),
                List.of(),
                Instant.now().truncatedTo(ChronoUnit.MILLIS),
                "tier2.quality");
    }

    void saveSupplierKey(String kid) {
        @SuppressWarnings("unchecked")
        var keys = (List<Map<String, Object>>) publicKeys.publicJwkSet().get("keys");
        partnerKeys.save(s.supplierBpnl, kid, keys.getFirst(), Instant.now());
    }

    String requestEnvelope(UUID exchangeId, String customerPartNumber) {
        var request = new RequestMessage(
                exchangeId,
                "PPF-2026-90009",
                s.customerBpnl,
                ApprovalScenario.TENANT,
                customerPartNumber,
                null,
                "Türgriff",
                new RequestMessage.CodeName("NEW_PART", "Neuteil", "New part"),
                new RequestMessage.CodeName("VDA-2", "Stufe 2", "Level 2"),
                LocalDate.now().plusDays(30),
                null,
                Instant.now().truncatedTo(ChronoUnit.MILLIS),
                "oem.quality",
                List.of(new RequestMessage.Element(
                        s.docElement,
                        "REQUIRED",
                        null,
                        new RequestMessage.Snapshot("Deckblatt", "Cover", "DOCUMENT", null))),
                List.of());
        EncodedPayload payload = codec.encodeRequest(request);
        return notification(MessageType.PPAP_REQUEST, payload, exchangeId, s.customerBpnl, null);
    }

    String envelope(EncodedPayload payload, String exchangeId, String jws) {
        return notification(MessageType.PPAP_SUBMISSION, payload, UUID.fromString(exchangeId), s.supplierBpnl, jws);
    }

    String notification(MessageType type, EncodedPayload payload, UUID exchangeId, String sender, String jws) {
        UUID messageId = UUID.randomUUID();
        var notification = new Notification(
                new Notification.Header(
                        messageId,
                        type.modelUrn(),
                        Instant.now().truncatedTo(ChronoUnit.MILLIS),
                        sender,
                        ApprovalScenario.TENANT,
                        null,
                        null,
                        null),
                type,
                exchangeId,
                "test-" + messageId,
                payload.canonical(),
                payload.hash(),
                jws);
        return new String(notifications.encode(notification), StandardCharsets.UTF_8);
    }

    MockHttpServletRequestBuilder receive(String body) throws Exception {
        String sender = json.readTree(body).get("header").get("senderBpn").asString();
        return post("/internal/edc/notifications")
                .header(SECRET_HEADER, SECRET)
                .header("Edc-Bpn", sender)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.getBytes(StandardCharsets.UTF_8));
    }

    JsonNode awaitSupplierCase(UUID exchangeId) {
        return await().atMost(Duration.ofSeconds(15))
                .until(
                        () -> {
                            JsonNode page = read(mvc.perform(get(CASES + "?ownRole=SUPPLIER&size=200")
                                            .with(supplier()))
                                    .andReturn());
                            for (JsonNode c : page.get("content")) {
                                if (exchangeId
                                        .toString()
                                        .equals(c.get("exchangeId").asString())) {
                                    return c;
                                }
                            }
                            return null;
                        },
                        c -> c != null);
    }

    void awaitStatus(String caseId, String expected) {
        await().atMost(Duration.ofSeconds(15))
                .until(() -> detail(caseId).get("status").asString(), expected::equals);
    }

    JsonNode detail(String caseId) throws Exception {
        return read(mvc.perform(get(CASES + "/" + caseId).with(tier1()))
                .andExpect(status().isOk())
                .andReturn());
    }

    JsonNode read(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        return body.isBlank() ? json.createObjectNode() : json.readTree(body);
    }

    static String pdfSha(String text) throws Exception {
        return sha(ApprovalScenario.pdf(text));
    }

    static String sha(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    /** Stream helper for JSON arrays. */
    static final class LiveStream {
        static java.util.stream.Stream<JsonNode> of(JsonNode array) {
            return java.util.stream.StreamSupport.stream(array.spliterator(), false);
        }
    }
}
