package com.msg.ppap.approval;

import static com.msg.ppap.approval.ApprovalScenario.engineer;
import static com.msg.ppap.approval.ApprovalScenario.supplier;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.msg.ppap.approval.application.port.in.InboundMessage;
import com.msg.ppap.approval.application.port.in.PartnerSharingUseCase;
import com.msg.ppap.approval.application.port.in.ReceiveRequestUseCase;
import com.msg.ppap.exchange.application.PullJobs;
import com.msg.ppap.exchange.application.PullPollingService;
import com.msg.ppap.exchange.application.SubmissionPullService;
import com.msg.ppap.exchange.application.port.out.CatalogOffer;
import com.msg.ppap.exchange.application.port.out.DataPlaneResponse;
import com.msg.ppap.exchange.application.port.out.DataPlaneStream;
import com.msg.ppap.exchange.application.port.out.DataspaceConnector;
import com.msg.ppap.exchange.application.port.out.EndpointDataReference;
import com.msg.ppap.exchange.application.port.out.PartnerKeyStore;
import com.msg.ppap.identity.PayloadSigner;
import com.msg.ppap.identity.PublicKeys;
import com.msg.ppap.semantics.api.AasDescriptors;
import com.msg.ppap.semantics.api.DigitalTwinIds;
import com.msg.ppap.semantics.api.DocumentReference;
import com.msg.ppap.semantics.api.EncodedPayload;
import com.msg.ppap.semantics.api.ExchangeCodec;
import com.msg.ppap.semantics.api.RequestMessage;
import com.msg.ppap.semantics.api.SubmissionMessage;
import com.msg.ppap.support.AbstractIntegrationTest;
import com.msg.ppap.support.TestJwts;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Phase 5 with Testcontainers: authorization of the internal EDC content endpoints (shared secret, {@code Edc-Bpn},
 * drafts and foreign partners never), mode recording and twin task on submission, and the customer pull job with a
 * stubbed dataspace connector (DTR lookup, shell descriptor, submodel, signature, documents with hash verification).
 */
@TestPropertySource(
        properties = {
            "ppap.edc.callback-secret=test-callback-secret",
            "ppap.exchange.scheduling-enabled=false",
            "ppap.exchange.max-attempts=2"
        })
class PullIntegrationTest extends AbstractIntegrationTest {

    static final String SECRET_HEADER = "X-Ppap-Edc-Secret";
    static final String SECRET = "test-callback-secret";
    static final String CASES = "/api/v1/approval-cases";

    @Autowired
    MockMvc mvc;

    @Autowired
    JsonMapper json;

    @Autowired
    ExchangeCodec codec;

    @Autowired
    ReceiveRequestUseCase requests;

    @Autowired
    PartnerSharingUseCase sharing;

    @Autowired
    SubmissionPullService pulls;

    @Autowired
    PullJobs jobs;

    @Autowired
    PullPollingService poller;

    @Autowired
    PayloadSigner signer;

    @Autowired
    PublicKeys publicKeys;

    @Autowired
    PartnerKeyStore partnerKeys;

    @Autowired
    JdbcTemplate jdbc;

    @MockitoBean
    DataspaceConnector connector;

    ApprovalScenario s;

    @BeforeEach
    void setUp() throws Exception {
        s = new ApprovalScenario(mvc, json).create();
    }

    // ------------------------------------------------------------------------------------------ supplier side

    @Test
    void internalEndpointsServeOnlySubmittedContentToTheCasePartner() throws Exception {
        UUID exchangeId = UUID.randomUUID();
        byte[] attachment = ApprovalScenario.pdf("attachment " + exchangeId);
        requests.receiveRequest(new InboundMessage(
                s.customerBpnl,
                request(exchangeId, List.of(reference("anhang.pdf", attachment)))
                        .canonical(),
                null));
        String caseId =
                sharing.findCase(exchangeId, "SUPPLIER").orElseThrow().caseId().toString();
        mvc.perform(post(CASES + "/" + caseId + "/start-work").with(supplier())).andExpect(status().isOk());
        String documentId = s.uploadPdf("evidence-" + exchangeId);
        String evidenceSha = sha(ApprovalScenario.pdf("evidence-" + exchangeId));
        String draft = CASES + "/" + caseId + "/submissions/draft/elements/" + s.docElement;
        mvc.perform(put(draft)
                        .with(supplier())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"selfAssessment\":\"OK\"}"))
                .andExpect(status().isOk());
        mvc.perform(post(draft + "/documents")
                        .with(supplier())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"documentId\":\"" + documentId + "\"}"))
                .andExpect(status().isOk());

        String submodel = "/internal/edc/submodels/" + exchangeId + "/1";
        // drafts are never shared
        mvc.perform(internal(submodel, s.customerBpnl)).andExpect(status().isNotFound());
        mvc.perform(internal("/internal/edc/documents/" + evidenceSha, s.customerBpnl))
                .andExpect(status().isNotFound());

        mvc.perform(post(CASES + "/" + caseId + "/submissions/draft/submit")
                        .with(supplier())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmation\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exchangeMode").value("PUSH_NOTIFY_PULL_PAYLOAD"));

        // shared secret and consumer identity
        mvc.perform(get(submodel)).andExpect(status().isUnauthorized());
        mvc.perform(get(submodel).header(SECRET_HEADER, SECRET)).andExpect(status().isForbidden());
        mvc.perform(internal(submodel, s.supplierBpnl)).andExpect(status().isNotFound());
        mvc.perform(internal("/internal/edc/submodels/" + exchangeId + "/2", s.customerBpnl))
                .andExpect(status().isNotFound());
        mvc.perform(internal("/internal/edc/submodels/not-a-uuid/1", s.customerBpnl))
                .andExpect(status().isNotFound());
        byte[] payload = mvc.perform(internal(submodel, s.customerBpnl))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
        mvc.perform(internal(submodel + "/signature", s.customerBpnl))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payloadHash").value("sha256:" + sha(payload)))
                .andExpect(jsonPath("$.signature").isNotEmpty());
        mvc.perform(internal(submodel + "/signature", s.supplierBpnl)).andExpect(status().isNotFound());

        // documents: evidence of the submitted revision for the customer only
        mvc.perform(internal("/internal/edc/documents/" + evidenceSha, s.customerBpnl))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"));
        mvc.perform(internal("/internal/edc/documents/" + evidenceSha, s.supplierBpnl))
                .andExpect(status().isNotFound());
        mvc.perform(internal("/internal/edc/documents/xyz", s.customerBpnl)).andExpect(status().isNotFound());

        // the twin is registered (local outbox task) before the notification of the same exchange id
        List<String> outbound = jdbc.queryForList(
                "select message_type from ex_message where exchange_id = ? and direction = 'OUTBOUND' order by seq",
                String.class,
                exchangeId);
        assertThat(outbound).containsSubsequence("TWIN_REGISTRATION", "PPAP_SUBMISSION_AVAILABLE");

        // the twin endpoint: no registration (no DTR in the test) → 404; customer role → 403
        mvc.perform(get("/api/v1/twins/" + exchangeId).with(supplier())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/twins/" + exchangeId).with(engineer())).andExpect(status().isForbidden());
    }

    @Test
    void sentRequestAttachmentsAreSharedWithTheSupplierOnly() throws Exception {
        String caseId = createCustomerCase();
        String attachmentId = s.uploadPdf("customer-attachment-" + caseId);
        String sha = sha(ApprovalScenario.pdf("customer-attachment-" + caseId));
        mvc.perform(post(CASES + "/" + caseId + "/request/attachments")
                        .with(engineer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"documentId\":\"" + attachmentId + "\"}"))
                .andExpect(status().isOk());
        mvc.perform(internal("/internal/edc/documents/" + sha, s.supplierBpnl)).andExpect(status().isNotFound());
        mvc.perform(post(CASES + "/" + caseId + "/send").with(engineer())).andExpect(status().isOk());
        mvc.perform(internal("/internal/edc/documents/" + sha, s.supplierBpnl)).andExpect(status().isOk());
        mvc.perform(internal("/internal/edc/documents/" + sha, s.customerBpnl)).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------------------------------ customer side

    @Test
    void announcedSubmissionIsPulledVerifiedAndItsDocumentsFetched() throws Exception {
        String caseId = createAndSendCustomerCase();
        UUID exchangeId = exchangeId(caseId);
        byte[] evidence = ApprovalScenario.pdf("supplier evidence " + exchangeId);
        Signed submission = signedSubmission(exchangeId, 1, evidence);
        stubSupplier(exchangeId, submission, evidence, true);

        assertThat(pulls.schedule(
                        s.supplierBpnl,
                        exchangeId,
                        1,
                        submission.payload().hash(),
                        SubmissionPullService.Trigger.NOTIFICATION,
                        null,
                        List.of()))
                .isTrue();
        assertThat(pulls.schedule(
                        s.supplierBpnl,
                        exchangeId,
                        1,
                        submission.payload().hash(),
                        SubmissionPullService.Trigger.NOTIFICATION,
                        null,
                        List.of()))
                .isFalse();
        jobs.runDue();

        JsonNode detail = getJson(CASES + "/" + caseId);
        assertThat(detail.path("status").asString()).isEqualTo("SUBMITTED");
        JsonNode revision = detail.path("submissions").path(0);
        assertThat(revision.path("exchangeMode").asString()).isEqualTo("PUSH_NOTIFY_PULL_PAYLOAD");
        assertThat(revision.path("signature").path("status").asString()).isEqualTo("VALID");
        assertThat(revision.path("payloadHash").asString())
                .isEqualTo(submission.payload().hash());

        JsonNode document = getJson(CASES + "/" + caseId + "/submissions/1")
                .path("elements")
                .path(0)
                .path("documents")
                .path(0);
        assertThat(document.path("transferStatus").asString()).isEqualTo("FETCHED");
        assertThat(document.path("hashVerified").asBoolean()).isTrue();
        assertThat(document.path("transferError").isNull()).isTrue();
        byte[] stored = mvc.perform(
                        get("/api/v1/documents/" + document.path("id").asString() + "/content")
                                .with(engineer()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
        assertThat(stored).isEqualTo(evidence);

        JsonNode records = getJson(CASES + "/" + caseId + "/exchange");
        JsonNode pull = find(records, "PULL_SUBMISSION");
        assertThat(pull.path("status").asString()).isEqualTo("PROCESSED");
        assertThat(steps(pull))
                .containsSubsequence(
                        "DTR/DTR_LOOKUP",
                        "DTR/SHELL_DESCRIPTOR",
                        "SUBMODEL/SUBMODEL_FETCH",
                        "SUBMODEL/HASH_CHECK",
                        "SUBMODEL/SIGNATURE_CHECK",
                        "SUBMODEL/PROCESSING");
        JsonNode documentPull = find(records, "PULL_DOCUMENT");
        assertThat(documentPull.path("status").asString()).isEqualTo("PROCESSED");
        assertThat(steps(documentPull)).contains("DOCUMENT/DOCUMENT_FETCH", "DOCUMENT/HASH_CHECK");

        // cockpit filter by record type
        mvc.perform(get("/api/v1/exchange/messages?messageType=PULL_SUBMISSION,PULL_DOCUMENT&size=200")
                        .with(TestJwts.user("admin", "CONNECTOR_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].messageType")
                        .value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.startsWith("PULL_"))));
    }

    @Test
    void documentWithWrongContentIsRejectedAndCanBeFetchedAgain() throws Exception {
        String caseId = createAndSendCustomerCase();
        UUID exchangeId = exchangeId(caseId);
        byte[] evidence = ApprovalScenario.pdf("announced " + exchangeId);
        Signed submission = signedSubmission(exchangeId, 1, evidence);
        stubSupplier(exchangeId, submission, ApprovalScenario.pdf("tampered " + exchangeId), true);
        pulls.schedule(
                s.supplierBpnl, exchangeId, 1, null, SubmissionPullService.Trigger.NOTIFICATION, null, List.of());
        jobs.runDue();

        JsonNode document = getJson(CASES + "/" + caseId + "/submissions/1")
                .path("elements")
                .path(0)
                .path("documents")
                .path(0);
        assertThat(document.path("transferStatus").asString()).isEqualTo("FAILED");
        assertThat(document.path("transferError").asString()).isEqualTo("DOCUMENT_HASH_MISMATCH");
        assertThat(document.path("hashVerified").asBoolean()).isFalse();
        assertThat(document.path("id").isNull()).isTrue();

        String sha = sha(evidence);
        mvc.perform(post(CASES + "/" + caseId + "/documents/sha256:" + sha + "/fetch")
                        .with(engineer()))
                .andExpect(status().isBadRequest());
        stubSupplier(exchangeId, submission, evidence, true);
        mvc.perform(post(CASES + "/" + caseId + "/documents/" + sha + "/fetch").with(engineer()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.transferStatus").value("PENDING"));
        org.awaitility.Awaitility.await()
                .atMost(java.time.Duration.ofSeconds(15))
                .untilAsserted(() -> {
                    jobs.runDue();
                    assertThat(getJson(CASES + "/" + caseId + "/submissions/1")
                                    .path("elements")
                                    .path(0)
                                    .path("documents")
                                    .path(0)
                                    .path("transferStatus")
                                    .asString())
                            .isEqualTo("FETCHED");
                });
    }

    @Test
    void pulledPayloadWithAnotherHashThanAnnouncedIsRejected() throws Exception {
        String caseId = createAndSendCustomerCase();
        UUID exchangeId = exchangeId(caseId);
        byte[] evidence = ApprovalScenario.pdf("x " + exchangeId);
        Signed submission = signedSubmission(exchangeId, 1, evidence);
        stubSupplier(exchangeId, submission, evidence, true);
        pulls.schedule(
                s.supplierBpnl,
                exchangeId,
                1,
                "sha256:" + "0".repeat(64),
                SubmissionPullService.Trigger.NOTIFICATION,
                null,
                List.of());
        jobs.runDue();
        JsonNode pull = find(getJson(CASES + "/" + caseId + "/exchange"), "PULL_SUBMISSION");
        assertThat(pull.path("status").asString()).isEqualTo("REJECTED");
        assertThat(pull.path("errorCode").asString()).isEqualTo("REJECTED_PAYLOAD_HASH_MISMATCH");
        assertThat(getJson(CASES + "/" + caseId).path("status").asString()).isEqualTo("REQUESTED");
    }

    @Test
    void pollerFindsNewRevisionsInTheSuppliersDtr() throws Exception {
        String caseId = createAndSendCustomerCase();
        UUID exchangeId = exchangeId(caseId);
        byte[] evidence = ApprovalScenario.pdf("polled " + exchangeId);
        Signed submission = signedSubmission(exchangeId, 1, evidence);
        // first round: twin not visible yet
        stubSupplier(exchangeId, submission, evidence, false);
        poller.poll();
        assertThat(jdbc.queryForObject(
                        "select count(*) from ex_message where exchange_id = ? and message_type = 'PULL_SUBMISSION'",
                        Integer.class,
                        exchangeId))
                .isZero();
        stubSupplier(exchangeId, submission, evidence, true);
        assertThat(poller.poll()).isPositive();
        jobs.runDue();
        JsonNode revision = getJson(CASES + "/" + caseId).path("submissions").path(0);
        assertThat(revision.path("exchangeMode").asString()).isEqualTo("PULL");
        // nothing new: the job of the next revision does not exist, the shell has no revision 2
        assertThat(poller.poll()).isZero();
    }

    // ------------------------------------------------------------------------------------------ helpers

    record Signed(EncodedPayload payload, String jws) {}

    /** Stubs the supplier's EDC/DTR/submodel/document endpoints behind the dataspace connector port. */
    void stubSupplier(UUID exchangeId, Signed submission, byte[] document, boolean twinVisible) {
        var edr = new EndpointDataReference("tp-1", "agr-1", URI.create("http://supplier-dp/api/public"), "tok", true);
        when(connector.requestCatalogByType(any(), anyString()))
                .thenReturn(List.of(new CatalogOffer("ppap-dtr", "offer-1", Map.of())));
        when(connector.obtainEndpoint(any(), any(CatalogOffer.class), any())).thenReturn(edr);
        when(connector.obtainEndpoint(any(), anyString(), any())).thenReturn(edr);
        String shellId = DigitalTwinIds.shellId(exchangeId);
        byte[] shell = AasDescriptors.shellDescriptor(new AasDescriptors.TwinSpec(
                exchangeId,
                "PPF-T-1",
                ApprovalScenario.TENANT,
                s.supplierBpnl,
                s.customerPartNumber,
                s.supplierPartNumberAtCustomer,
                List.of(new AasDescriptors.SubmodelSpec(
                        1, "http://supplier-dp/api/public/" + exchangeId + "/1", "http://edc.example/api/v1/dsp"))));
        when(connector.call(any(), anyString(), anyString(), any(), any(), anyMap()))
                .thenAnswer(invocation -> {
                    String method = invocation.getArgument(1);
                    String path = invocation.getArgument(2);
                    if ("POST".equals(method)) {
                        return new DataPlaneResponse(200, "{}".getBytes(StandardCharsets.UTF_8));
                    }
                    if (path.startsWith("/lookup/shells")) {
                        String ids = twinVisible ? "\"" + shellId + "\"" : "";
                        return new DataPlaneResponse(
                                200, ("{\"result\":[" + ids + "]}").getBytes(StandardCharsets.UTF_8));
                    }
                    if (path.startsWith("/shell-descriptors/")) {
                        return twinVisible
                                ? new DataPlaneResponse(200, shell)
                                : new DataPlaneResponse(404, new byte[0]);
                    }
                    if (path.equals("/" + exchangeId + "/1")) {
                        return new DataPlaneResponse(200, submission.payload().canonical());
                    }
                    if (path.equals("/" + exchangeId + "/1/signature")) {
                        return new DataPlaneResponse(
                                200,
                                json.writeValueAsBytes(Map.of(
                                        "payloadHash", submission.payload().hash(), "signature", submission.jws())));
                    }
                    return new DataPlaneResponse(404, new byte[0]);
                });
        when(connector.stream(any(), anyString(), anyMap()))
                .thenAnswer(invocation ->
                        new DataPlaneStream(200, new ByteArrayInputStream(document), document.length, Map.of()));
    }

    Signed signedSubmission(UUID exchangeId, int revision, byte[] evidence) {
        EncodedPayload payload = codec.encodeSubmission(new SubmissionMessage(
                exchangeId,
                "PPF-2026-77777",
                revision,
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
                                List.of(reference("evidence.pdf", evidence))),
                        new SubmissionMessage.Element(
                                s.valueElement, "REQUIRED", "OK", Map.of("quantity", 2), null, List.of())),
                List.of(),
                Instant.now().truncatedTo(ChronoUnit.MILLIS),
                "tier1.quality"));
        var signature = signer.sign(payload.canonical());
        @SuppressWarnings("unchecked")
        var keys = (List<Map<String, Object>>) publicKeys.publicJwkSet().get("keys");
        partnerKeys.save(s.supplierBpnl, signature.kid(), keys.getFirst(), Instant.now());
        return new Signed(payload, signature.jws());
    }

    EncodedPayload request(UUID exchangeId, List<DocumentReference> attachments) {
        return codec.encodeRequest(new RequestMessage(
                exchangeId,
                "PPF-2026-90001",
                s.customerBpnl,
                ApprovalScenario.TENANT,
                s.customerNumberAtSupplier,
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
                attachments));
    }

    static DocumentReference reference(String name, byte[] content) {
        return new DocumentReference(name, "application/pdf", content.length, sha(content));
    }

    String createCustomerCase() throws Exception {
        return json.readTree(mvc.perform(post(CASES)
                                .with(engineer())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(s.toJson(Map.of(
                                        "partId", s.customerPartId,
                                        "reasonCode", s.reason,
                                        "levelCode", s.level,
                                        "dueDate", LocalDate.now().plusDays(20).toString()))))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(StandardCharsets.UTF_8))
                .path("id")
                .asString();
    }

    String createAndSendCustomerCase() throws Exception {
        String caseId = createCustomerCase();
        mvc.perform(post(CASES + "/" + caseId + "/send").with(engineer())).andExpect(status().isOk());
        return caseId;
    }

    UUID exchangeId(String caseId) throws Exception {
        return UUID.fromString(getJson(CASES + "/" + caseId).path("exchangeId").asString());
    }

    JsonNode getJson(String path) throws Exception {
        return json.readTree(mvc.perform(get(path).with(engineer()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8));
    }

    static JsonNode find(JsonNode records, String type) {
        for (JsonNode r : records) {
            if (type.equals(r.path("messageType").asString())) {
                return r;
            }
        }
        throw new AssertionError("no " + type + " in " + records);
    }

    static List<String> steps(JsonNode record) {
        var steps = new java.util.ArrayList<String>();
        record.path("steps")
                .forEach(st -> steps.add(
                        st.path("target").asString() + "/" + st.path("step").asString()));
        return steps;
    }

    static MockHttpServletRequestBuilder internal(String path, String edcBpn) {
        return get(path).header(SECRET_HEADER, SECRET).header("Edc-Bpn", edcBpn);
    }

    static String sha(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
