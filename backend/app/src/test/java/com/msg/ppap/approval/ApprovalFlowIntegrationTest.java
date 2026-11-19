package com.msg.ppap.approval;

import static com.msg.ppap.approval.ApprovalScenario.engineer;
import static com.msg.ppap.approval.ApprovalScenario.supplier;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.msg.ppap.approval.application.port.in.InboundMessage;
import com.msg.ppap.approval.application.port.in.ReceiveDecisionUseCase;
import com.msg.ppap.approval.application.port.in.ReceiveRequestUseCase;
import com.msg.ppap.approval.application.port.in.ReceiveSubmissionUseCase;
import com.msg.ppap.exchange.application.port.out.PartnerKeyStore;
import com.msg.ppap.identity.PayloadSigner;
import com.msg.ppap.identity.PublicKeys;
import com.msg.ppap.semantics.api.DecisionMessage;
import com.msg.ppap.semantics.api.EncodedPayload;
import com.msg.ppap.semantics.api.ExchangeCodec;
import com.msg.ppap.semantics.api.RequestMessage;
import com.msg.ppap.semantics.api.SubmissionMessage;
import com.msg.ppap.support.AbstractIntegrationTest;
import com.msg.ppap.support.TestJwts;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** End-to-end approval flow on both sides of one tenant (phase 3: partner messages via the inbound ports). */
class ApprovalFlowIntegrationTest extends AbstractIntegrationTest {

    static final String CASES = "/api/v1/approval-cases";

    @Autowired
    MockMvc mvc;

    @Autowired
    JsonMapper json;

    @Autowired
    ExchangeCodec codec;

    @Autowired
    PayloadSigner signer;

    @Autowired
    ReceiveRequestUseCase receiveRequest;

    @Autowired
    ReceiveSubmissionUseCase receiveSubmission;

    @Autowired
    ReceiveDecisionUseCase receiveDecision;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PublicKeys publicKeys;

    @Autowired
    PartnerKeyStore partnerKeys;

    ApprovalScenario s;

    @BeforeEach
    void setUp() throws Exception {
        s = new ApprovalScenario(mvc, json).create();
    }

    @Test
    void customerRequestsReceivesSubmissionAndDecides() throws Exception {
        // create draft: elements prefilled from the level (REQUIRED only)
        MvcResult created = mvc.perform(post(CASES)
                        .with(engineer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(s.toJson(Map.of(
                                "partId", s.customerPartId,
                                "reasonCode", s.reason,
                                "levelCode", s.level,
                                "dueDate", LocalDate.now().plusDays(20).toString(),
                                "remarks", "Neuteil"))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("http://localhost/api/v1/approval-cases/")))
                .andExpect(header().string("ETag", "\"0\""))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.ownRole").value("CUSTOMER"))
                .andExpect(jsonPath("$.source").value("LOCAL"))
                .andExpect(jsonPath("$.caseNumber").value(startsWith("PPF-")))
                .andExpect(jsonPath("$.partner.bpnl").value(s.supplierBpnl))
                .andExpect(jsonPath("$.relationId").value(s.supplierRelationId.toString()))
                .andExpect(jsonPath("$.customerPartNumber").value(s.customerPartNumber))
                .andExpect(jsonPath("$.supplierPartNumber").value(s.supplierPartNumberAtCustomer))
                .andExpect(jsonPath("$.request.elements[*].code").value(contains(s.docElement, s.valueElement)))
                .andExpect(jsonPath("$.request.elements[1].snapshot.valueSchema.required[0]")
                        .value("quantity"))
                .andExpect(jsonPath("$.allowedActions").value(contains("EDIT", "SEND", "CANCEL")))
                .andExpect(jsonPath("$.timeline[0].action").value("CREATE"))
                .andReturn();
        JsonNode draft = body(created);
        String id = draft.get("id").asString();
        UUID exchangeId = UUID.fromString(draft.get("exchangeId").asString());

        // edit draft: element list incl. optional element, attachment
        mvc.perform(put(CASES + "/" + id + "/request/elements")
                        .with(engineer())
                        .header("If-Match", "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(s.toJson(List.of(
                                Map.of("code", s.docElement),
                                Map.of("code", s.valueElement, "note", "5 Teile"),
                                Map.of("code", s.optionalElement)))))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"1\""))
                .andExpect(jsonPath("$.request.elements[2].requirement").value("ON_REQUEST"));
        String attachment = s.uploadPdf("zeichnung.pdf");
        mvc.perform(post(CASES + "/" + id + "/request/attachments")
                        .with(engineer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(s.toJson(Map.of("documentId", attachment))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.request.attachments[0].fileName").value("zeichnung.pdf"));

        // version conflict, wrong user role, then send
        mvc.perform(post(CASES + "/" + id + "/send").with(engineer()).header("If-Match", "\"0\""))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
        mvc.perform(post(CASES + "/" + id + "/send").with(supplier())).andExpect(status().isForbidden());
        mvc.perform(post(CASES + "/" + id + "/send").with(engineer()).header("If-Match", "\"2\""))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"3\""))
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andExpect(jsonPath("$.request.requestedBy").value("oem.quality"))
                .andExpect(jsonPath("$.allowedActions").value(contains("CANCEL")));
        mvc.perform(post(CASES + "/" + id + "/send").with(engineer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"))
                .andExpect(jsonPath("$.currentStatus").value("REQUESTED"))
                .andExpect(jsonPath("$.action").value("SEND"));
        mvc.perform(post(CASES + "/" + id + "/start-work").with(TestJwts.user("x", "SYSTEM_INTEGRATION")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WRONG_CASE_ROLE"));

        // supplier's signed submission arrives
        EncodedPayload submission = codec.encodeSubmission(new SubmissionMessage(
                exchangeId,
                "PPF-2026-77777",
                1,
                ApprovalScenario.TENANT,
                s.supplierBpnl,
                s.customerPartNumber,
                s.supplierPartNumberAtCustomer,
                s.reason,
                s.level,
                List.of(
                        new SubmissionMessage.Element(s.docElement, "REQUIRED", "OK", null, null, List.of()),
                        new SubmissionMessage.Element(
                                s.valueElement, "REQUIRED", "OK", Map.of("quantity", 2), null, List.of())),
                List.of(),
                Instant.now().truncatedTo(ChronoUnit.MILLIS),
                "tier1.quality"));
        var signature = signer.sign(submission.canonical());
        String jws = signature.jws();
        // the supplier's public key is known (in production fetched via EDC, asset ppap-public-keys)
        @SuppressWarnings("unchecked")
        var keys = (List<Map<String, Object>>) publicKeys.publicJwkSet().get("keys");
        partnerKeys.save(s.supplierBpnl, signature.kid(), keys.getFirst(), Instant.now());
        var inbound = new InboundMessage(s.supplierBpnl, submission.canonical(), jws);
        assertThat(receiveSubmission.receiveSubmission(inbound).applied()).isTrue();
        assertThat(receiveSubmission.receiveSubmission(inbound).applied()).isFalse();

        mvc.perform(get(CASES + "/" + id).with(engineer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.submissions[0].payloadHash").value(submission.hash()))
                .andExpect(jsonPath("$.submissions[0].signature.status").value("VALID"))
                .andExpect(jsonPath("$.allowedActions").value(contains("START_REVIEW")));

        // review and decision
        mvc.perform(post(CASES + "/" + id + "/start-review").with(supplier())).andExpect(status().isForbidden());
        mvc.perform(post(CASES + "/" + id + "/start-review").with(engineer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_REVIEW"))
                .andExpect(jsonPath("$.allowedActions").value(contains("REVIEW", "DECIDE")));
        mvc.perform(put(CASES + "/" + id + "/review/elements/" + s.valueElement)
                        .with(engineer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"result\":\"NOT_ACCEPTED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewFindings[0].result").value("NOT_ACCEPTED"));
        mvc.perform(get(CASES + "/" + id + "/submissions/1").with(engineer()))
                .andExpect(jsonPath("$.elements[1].finding.result").value("NOT_ACCEPTED"))
                .andExpect(jsonPath("$.elements[0].finding").doesNotExist());
        mvc.perform(post(CASES + "/" + id + "/decision")
                        .with(engineer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"result\":\"REJECTED\",\"confirmation\":true}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("REJECTION_REASON_REQUIRED"))
                .andExpect(jsonPath("$.errors[0].field").value("elementFindings." + s.valueElement + ".comment"));
        mvc.perform(put(CASES + "/" + id + "/review/elements/" + s.valueElement)
                        .with(engineer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"result\":\"NOT_ACCEPTED\",\"comment\":\"Nur 2 Muster\"}"))
                .andExpect(status().isOk());
        mvc.perform(post(CASES + "/" + id + "/decision")
                        .with(engineer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"result\":\"REJECTED\",\"confirmation\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CONFIRMATION_REQUIRED"));
        mvc.perform(post(CASES + "/" + id + "/decision")
                        .with(engineer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"result\":\"MAYBE\",\"confirmation\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        JsonNode decision = body(mvc.perform(post(CASES + "/" + id + "/decision")
                        .with(engineer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"result\":\"REJECTED\",\"comment\":\"Bitte nachliefern\",\"confirmation\":true}"))
                .andExpect(status().isOk())
                .andExpect(header().exists("ETag"))
                .andExpect(jsonPath("$.result").value("REJECTED"))
                .andExpect(jsonPath("$.decidedBy").value("oem.quality"))
                .andExpect(jsonPath("$.signature.status").value("VALID"))
                .andReturn());
        MvcResult payload = mvc.perform(get(CASES + "/" + id + "/submissions/1/decision/payload")
                        .with(engineer()))
                .andExpect(status().isOk())
                .andExpect(header().string(
                                "X-Payload-Hash", decision.get("payloadHash").asString()))
                .andReturn();
        String decisionPayload = payload.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(decisionPayload).contains("\"submissionHash\":\"" + submission.hash() + "\"");
        assertThat(verifyAgainstJwks(
                        decisionPayload.getBytes(StandardCharsets.UTF_8),
                        payload.getResponse().getHeader("X-Signature")))
                .isTrue();
        mvc.perform(get(CASES + "/" + id).with(engineer()))
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.lastDecision.result").value("REJECTED"))
                .andExpect(jsonPath("$.lastDecision.elementFindings[0].comment").value("Nur 2 Muster"))
                .andExpect(jsonPath("$.timeline[*].action")
                        .value(contains("CREATE", "SEND", "RECEIVE_SUBMISSION", "START_REVIEW", "DECIDE")));
        mvc.perform(get(CASES).with(engineer()).param("q", s.customerPartNumber))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].lastDecisionResult").value("REJECTED"));

        // DB-level immutability of decisions and submitted revisions
        assertThat(rejectedByTrigger("update ap_decision set comment = 'x' where case_id = '" + id + "'"))
                .isTrue();
        assertThat(rejectedByTrigger("update ap_submission set payload = '{}' where case_id = '" + id + "'"))
                .isTrue();
        assertThat(rejectedByTrigger("delete from ap_timeline_entry where case_id = '" + id + "'"))
                .isTrue();

        // audit trail of the case
        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(jdbc.queryForList(
                                "select action from audit_event where object_type = 'APPROVAL_CASE' and object_id = ?",
                                String.class,
                                id))
                        .contains("CREATE", "SEND", "RECEIVE_SUBMISSION", "START_REVIEW", "DECIDE"));
    }

    @Test
    void supplierReceivesRequestSubmitsAndReceivesDecision() throws Exception {
        UUID exchangeId = UUID.randomUUID();
        EncodedPayload request = codec.encodeRequest(new RequestMessage(
                exchangeId,
                "PPF-2026-55555",
                s.customerBpnl,
                ApprovalScenario.TENANT,
                s.customerNumberAtSupplier,
                null,
                "Türgriff",
                new RequestMessage.CodeName(s.reason, "Neuteil", "New part"),
                new RequestMessage.CodeName(s.level, "Stufe", "Level"),
                LocalDate.now().plusDays(14),
                "Bitte bis KW 50",
                Instant.now().truncatedTo(ChronoUnit.MILLIS),
                "oem.quality",
                List.of(
                        new RequestMessage.Element(
                                s.docElement,
                                "REQUIRED",
                                null,
                                new RequestMessage.Snapshot("Deckblatt", "Cover", "DOCUMENT", null)),
                        new RequestMessage.Element(
                                s.valueElement,
                                "REQUIRED",
                                "5 Teile",
                                new RequestMessage.Snapshot(
                                        "Erstmuster", "Initial samples", "VALUES", ApprovalScenario.VALUE_SCHEMA))),
                List.of()));
        var received = receiveRequest.receiveRequest(new InboundMessage(s.customerBpnl, request.canonical(), null));
        assertThat(received.applied()).isTrue();
        assertThat(receiveRequest
                        .receiveRequest(new InboundMessage(s.customerBpnl, request.canonical(), null))
                        .caseId())
                .isEqualTo(received.caseId());
        String id = received.caseId().toString();

        mvc.perform(get(CASES + "/" + id).with(supplier()))
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andExpect(jsonPath("$.source").value("EXCHANGE"))
                .andExpect(jsonPath("$.part.partNumber").value(s.supplierPartNumber))
                .andExpect(jsonPath("$.customerPartNumber").value(s.customerNumberAtSupplier))
                .andExpect(jsonPath("$.allowedActions").value(contains("START_WORK")));
        mvc.perform(post(CASES + "/" + id + "/cancel")
                        .with(supplier())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"x\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WRONG_CASE_ROLE"));
        mvc.perform(post(CASES + "/" + id + "/start-work").with(supplier()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.draftCompleteness.requiredTotal").value(2));

        mvc.perform(post(CASES + "/" + id + "/submissions/draft/submit")
                        .with(supplier())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmation\":true}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INCOMPLETE_SUBMISSION"))
                .andExpect(jsonPath("$.errors[*].code").value(hasItem("MISSING_SELF_ASSESSMENT")));

        // invalid values are stored and reported
        String draftPath = CASES + "/" + id + "/submissions/draft";
        mvc.perform(put(draftPath + "/elements/" + s.valueElement)
                        .with(supplier())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"selfAssessment\":\"OK\",\"values\":{\"quantity\":0}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.elements[1].values.quantity").value(0))
                .andExpect(jsonPath("$.elements[1].complete").value(false))
                .andExpect(jsonPath("$.elements[1].issues[0].code").value("VALUE_SCHEMA_VIOLATION"))
                .andExpect(jsonPath("$.elements[1].issues[0].field").value("/quantity"));
        mvc.perform(put(draftPath + "/elements/" + s.valueElement)
                        .with(supplier())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"selfAssessment\":\"MAYBE\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put(draftPath + "/elements/NOPE")
                        .with(supplier())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"selfAssessment\":\"OK\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(put(draftPath + "/elements/" + s.valueElement)
                        .with(supplier())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"selfAssessment\":\"OK\",\"values\":{\"quantity\":5},\"comment\":\"5 Muster\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.elements[1].complete").value(true));
        // null clears a saved assessment
        mvc.perform(put(draftPath + "/elements/" + s.docElement)
                        .with(supplier())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"selfAssessment\":\"NA\",\"comment\":\"n/a\"}"))
                .andExpect(jsonPath("$.elements[0].selfAssessment").value("NA"));
        mvc.perform(put(draftPath + "/elements/" + s.docElement)
                        .with(supplier())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"selfAssessment\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.elements[0].selfAssessment").doesNotExist());
        mvc.perform(get(CASES)
                        .with(supplier())
                        .param("q", s.supplierPartNumber)
                        .param("status", "IN_PROGRESS,SUBMITTED"))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get(CASES)
                        .with(supplier())
                        .param("q", s.supplierPartNumber)
                        .param("status", "IN_PROGRESS")
                        .param("status", "SUBMITTED"))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get(CASES).with(supplier()).param("q", s.supplierPartNumber).param("status", "REQUESTED,SUBMITTED"))
                .andExpect(jsonPath("$.totalElements").value(0));
        String document = s.uploadPdf("deckblatt.pdf");
        String spare = s.uploadPdf("spare.pdf");
        mvc.perform(post(draftPath + "/elements/" + s.docElement + "/documents")
                        .with(supplier())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(s.toJson(Map.of("documentId", spare))))
                .andExpect(status().isOk());
        String etag = mvc.perform(delete(draftPath + "/elements/" + s.docElement + "/documents/" + spare)
                        .with(supplier()))
                .andExpect(status().isNoContent())
                .andReturn()
                .getResponse()
                .getHeader("ETag");
        mvc.perform(post(draftPath + "/elements/" + s.docElement + "/documents")
                        .with(supplier())
                        .header("If-Match", etag)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(s.toJson(Map.of("documentId", document))))
                .andExpect(status().isOk());
        mvc.perform(put(draftPath + "/elements/" + s.docElement)
                        .with(supplier())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"selfAssessment\":\"OK\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completeness.complete").value(true));

        JsonNode submitted = body(mvc.perform(post(draftPath + "/submit")
                        .with(supplier())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmation\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.signature.alg").value("ES256"))
                .andExpect(jsonPath("$.signature.status").value("VALID"))
                .andReturn());
        String submissionHash = submitted.get("payloadHash").asString();
        MvcResult payload = mvc.perform(
                        get(CASES + "/" + id + "/submissions/1/payload").with(engineer()))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Payload-Hash", submissionHash))
                .andReturn();
        byte[] payloadBytes = payload.getResponse().getContentAsByteArray();
        assertThat(new String(payloadBytes, StandardCharsets.UTF_8))
                .contains("\"exchangeId\":\"" + exchangeId + "\"")
                .contains("\"fileName\":\"deckblatt.pdf\"")
                .contains("\"submittedBy\":\"tier1.quality\"");
        assertThat(verifyAgainstJwks(payloadBytes, payload.getResponse().getHeader("X-Signature")))
                .isTrue();
        assertThat(verifyAgainstJwks(
                        (new String(payloadBytes, StandardCharsets.UTF_8) + " ").getBytes(StandardCharsets.UTF_8),
                        payload.getResponse().getHeader("X-Signature")))
                .isFalse();
        mvc.perform(post(CASES + "/" + id + "/start-work").with(supplier()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
        mvc.perform(get(draftPath).with(supplier())).andExpect(status().isNotFound());

        // customer's decision arrives (wrong hash first)
        DecisionMessage wrong = decision(exchangeId, "sha256:" + "0".repeat(64));
        EncodedPayload wrongPayload = codec.encodeDecision(wrong);
        assertThat(org.assertj.core.api.Assertions.catchThrowable(
                        () -> receiveDecision.receiveDecision(new InboundMessage(
                                s.customerBpnl,
                                wrongPayload.canonical(),
                                signer.sign(wrongPayload.canonical()).jws()))))
                .hasFieldOrPropertyWithValue("code", "PAYLOAD_HASH_MISMATCH");
        EncodedPayload decision = codec.encodeDecision(decision(exchangeId, submissionHash));
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> receiveDecision.receiveDecision(
                        new InboundMessage(s.customerBpnl, decision.canonical(), "eyJhbGciOiJFUzI1NiJ9..AAAA"))))
                .hasFieldOrPropertyWithValue("code", "SIGNATURE_INVALID");
        assertThat(receiveDecision
                        .receiveDecision(new InboundMessage(
                                s.customerBpnl,
                                decision.canonical(),
                                signer.sign(decision.canonical()).jws()))
                        .applied())
                .isTrue();
        mvc.perform(get(CASES + "/" + id).with(supplier()))
                .andExpect(jsonPath("$.status").value("CONDITIONALLY_APPROVED"))
                .andExpect(jsonPath("$.lastDecision.conditions[0].text").value("Prüfbericht nachreichen"))
                .andExpect(jsonPath("$.allowedActions").value(contains("NEW_REVISION")));

        // new revision prefilled from revision 1
        mvc.perform(post(CASES + "/" + id + "/submissions").with(supplier()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("http://localhost/api/v1/approval-cases/" + id)))
                .andExpect(jsonPath("$.revision").value(2))
                .andExpect(jsonPath("$.basedOnRevision").value(1))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.elements[1].values.quantity").value(5));
        mvc.perform(get(CASES + "/" + id + "/submissions").with(supplier()))
                .andExpect(jsonPath("$[0].decisionResult").value("CONDITIONALLY_APPROVED"))
                .andExpect(jsonPath("$[1].status").value("DRAFT"));
        mvc.perform(get(CASES + "/" + id + "/submissions/1").with(supplier()))
                .andExpect(jsonPath("$.decision.result").value("CONDITIONALLY_APPROVED"))
                .andExpect(jsonPath("$.elements[1].finding.result").value("ACCEPTED_WITH_CONDITION"));
    }

    @Test
    void manualCaptureAndCancel() throws Exception {
        JsonNode manual = body(mvc.perform(post(CASES + "/manual")
                        .with(supplier())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(s.toJson(Map.of(
                                "partnerBpnl", s.customerBpnl,
                                "partId", s.supplierPartId,
                                "reasonCode", s.reason,
                                "levelCode", s.level,
                                "dueDate", LocalDate.now().plusDays(10).toString(),
                                "externalReference", "Mail 2026-11-18"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andExpect(jsonPath("$.source").value("MANUAL"))
                .andExpect(jsonPath("$.partnerPartNumber").value(s.customerNumberAtSupplier))
                .andExpect(jsonPath("$.externalReference").value("Mail 2026-11-18"))
                .andExpect(jsonPath("$.timeline[0].action").value("CAPTURE_MANUAL"))
                .andExpect(jsonPath("$.allowedActions").value(contains("CANCEL", "START_WORK")))
                .andReturn());
        String id = manual.get("id").asString();
        mvc.perform(post(CASES + "/" + id + "/cancel")
                        .with(supplier())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(s.toJson(Map.of("reason", "x".repeat(501)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("TOO_LONG"));
        mvc.perform(post(CASES + "/" + id + "/cancel")
                        .with(supplier())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Kunde hat zurückgezogen\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.timeline[1].comment").value("Kunde hat zurückgezogen"));

        mvc.perform(post(CASES + "/manual")
                        .with(engineer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(post(CASES + "/manual")
                        .with(supplier())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(s.toJson(Map.of("partnerBpnl", s.supplierBpnl, "partId", UUID.randomUUID()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("partnerBpnl")))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("partId")))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("dueDate")));
    }

    @Test
    void createValidationAndSendGuards() throws Exception {
        mvc.perform(post(CASES)
                        .with(engineer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(s.toJson(Map.of(
                                "partId",
                                s.supplierPartId,
                                "reasonCode",
                                "UNKNOWN-1",
                                "levelCode",
                                s.level,
                                "dueDate",
                                "2020-01-01",
                                "elements",
                                List.of(Map.of("code", "NOPE-1"))))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("relationId")))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("reasonCode")))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("elements[0].code")));

        JsonNode created = body(mvc.perform(post(CASES)
                        .with(TestJwts.integration())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(s.toJson(Map.of(
                                "partId", s.customerPartId,
                                "relationId", s.supplierRelationId,
                                "reasonCode", s.reason,
                                "levelCode", s.level,
                                "dueDate", LocalDate.now().toString()))))
                .andExpect(status().isCreated())
                .andReturn());
        String id = created.get("id").asString();
        mvc.perform(post(CASES + "/" + id + "/send").with(engineer()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("DUE_DATE_IN_PAST"));
        mvc.perform(put(CASES + "/" + id)
                        .with(engineer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(s.toJson(Map.of(
                                "reasonCode",
                                s.reason,
                                "levelCode",
                                s.level,
                                "dueDate",
                                LocalDate.now().plusDays(3).toString(),
                                "remarks",
                                "neu"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remarks").value("neu"));
        mvc.perform(get(CASES + "/" + UUID.randomUUID()).with(engineer())).andExpect(status().isNotFound());
        mvc.perform(get(CASES).with(engineer()).param("status", "BOGUS")).andExpect(status().isBadRequest());
        mvc.perform(get(CASES)
                        .with(engineer())
                        .param("status", "DRAFT,REQUESTED")
                        .param("ownRole", "CUSTOMER")
                        .param("partnerBpnl", s.supplierBpnl)
                        .param("overdue", "false")
                        .param("sort", "dueDate,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].partner.bpnl").value(s.supplierBpnl))
                .andExpect(jsonPath("$.content[0].allowedActions").value(contains("EDIT", "SEND", "CANCEL")));
        mvc.perform(post(CASES + "/" + id + "/cancel")
                        .with(engineer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"falsch angelegt\"}"))
                .andExpect(status().isOk());
        mvc.perform(get(CASES + "/stats").with(engineer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.byStatus.CANCELLED").isNumber())
                .andExpect(jsonPath("$.open").isNumber());
    }

    @Test
    void catalogueReferencedByCasesIsOnlyDeactivated() throws Exception {
        mvc.perform(post(CASES)
                        .with(engineer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(s.toJson(Map.of(
                                "partId", s.customerPartId,
                                "reasonCode", s.reason,
                                "levelCode", s.level,
                                "dueDate", LocalDate.now().plusDays(3).toString()))))
                .andExpect(status().isCreated());
        JsonNode levels =
                body(mvc.perform(get("/api/v1/catalog/levels").with(engineer())).andReturn());
        String levelId = null;
        for (JsonNode level : levels) {
            if (level.get("code").asString().equals(s.level)) {
                levelId = level.get("id").asString();
            }
        }
        mvc.perform(delete("/api/v1/catalog/levels/" + levelId).with(TestJwts.manager()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/catalog/levels/" + levelId).with(engineer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        JsonNode reasons = body(
                mvc.perform(get("/api/v1/catalog/reasons").with(engineer())).andReturn());
        for (JsonNode reason : reasons) {
            if (reason.get("code").asString().equals(s.reason)) {
                String reasonId = reason.get("id").asString();
                mvc.perform(delete("/api/v1/catalog/reasons/" + reasonId).with(TestJwts.manager()))
                        .andExpect(status().isNoContent());
                mvc.perform(get("/api/v1/catalog/reasons/" + reasonId).with(engineer()))
                        .andExpect(jsonPath("$.active").value(false));
            }
        }
        // part mapping search across relations (request wizard)
        mvc.perform(get("/api/v1/part-mappings")
                        .with(engineer())
                        .param("partnerRole", "SUPPLIER")
                        .param("q", s.customerPartNumber))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].partner.bpnl").value(s.supplierBpnl))
                .andExpect(jsonPath("$.content[0].partner.exchangeReady").value(true))
                .andExpect(jsonPath("$.content[0].part.partNumber").value(s.customerPartNumber))
                .andExpect(jsonPath("$.content[0].partnerPartNumber").value(s.supplierPartNumberAtCustomer));
        mvc.perform(get("/api/v1/part-mappings").with(engineer()).param("partnerRole", "NOPE"))
                .andExpect(status().isBadRequest());
    }

    private DecisionMessage decision(UUID exchangeId, String submissionHash) {
        return new DecisionMessage(
                exchangeId,
                "PPF-2026-55555",
                1,
                submissionHash,
                "CONDITIONALLY_APPROVED",
                List.of("Prüfbericht nachreichen"),
                LocalDate.now().plusDays(30),
                null,
                List.of(new DecisionMessage.Finding(s.valueElement, "ACCEPTED_WITH_CONDITION", "Bericht fehlt")),
                Instant.now().truncatedTo(ChronoUnit.MILLIS),
                "oem.quality");
    }

    private boolean verifyAgainstJwks(byte[] payload, String jws) throws Exception {
        String jwks = mvc.perform(get("/.well-known/jwks.json"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JWSObject parsed = JWSObject.parse(jws, new Payload(payload));
        ECKey key = (ECKey) JWKSet.parse(jwks).getKeyByKeyId(parsed.getHeader().getKeyID());
        assertThat(parsed.getHeader().isBase64URLEncodePayload()).isFalse();
        return parsed.verify(new ECDSAVerifier(key.toECPublicKey(), Set.of("b64")));
    }

    private boolean rejectedByTrigger(String sql) {
        try {
            int updated = jdbc.update(sql);
            return updated == 0;
        } catch (RuntimeException ex) {
            return ex.getMessage().contains("immutable") || ex.getMessage().contains("append-only");
        }
    }

    private JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
}
