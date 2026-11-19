package com.msg.ppap.approval;

import static com.msg.ppap.approval.ApprovalScenario.engineer;
import static com.msg.ppap.approval.ApprovalScenario.supplier;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.msg.ppap.exchange.application.InboxService;
import com.msg.ppap.semantics.api.EncodedPayload;
import com.msg.ppap.semantics.api.ExchangeCodec;
import com.msg.ppap.semantics.api.Notification;
import com.msg.ppap.semantics.api.Notification.MessageType;
import com.msg.ppap.semantics.api.NotificationCodec;
import com.msg.ppap.semantics.api.RequestMessage;
import com.msg.ppap.semantics.api.StatusUpdateMessage;
import com.msg.ppap.support.AbstractIntegrationTest;
import com.msg.ppap.support.TestJwts;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Exchange with Testcontainers: receiver endpoint (shared secret) → inbox → asynchronous processing → case creation,
 * clarification list, duplicates, rejections; transactional outbox on send with retry while the EDC is unreachable;
 * cockpit endpoints and live event stream.
 */
@TestPropertySource(
        properties = {"ppap.edc.callback-secret=test-callback-secret", "ppap.exchange.worker-interval=200ms"})
class ExchangeIntegrationTest extends AbstractIntegrationTest {

    static final String RECEIVE = "/internal/edc/notifications";
    static final String SECRET_HEADER = "X-Ppap-Edc-Secret";
    static final String SECRET = "test-callback-secret";

    @Autowired
    MockMvc mvc;

    @Autowired
    JsonMapper json;

    @Autowired
    ExchangeCodec codec;

    @Autowired
    NotificationCodec notifications;

    @Autowired
    InboxService inbox;

    @Autowired
    JdbcTemplate jdbc;

    ApprovalScenario s;

    @BeforeEach
    void setUp() throws Exception {
        s = new ApprovalScenario(mvc, json).create();
    }

    @Test
    void receiverRequiresTheSharedSecret() throws Exception {
        mvc.perform(post(RECEIVE).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(RECEIVE)
                        .header(SECRET_HEADER, "wrong")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/internal/edc/jwks")).andExpect(status().isUnauthorized());
        mvc.perform(get("/internal/edc/jwks").header(SECRET_HEADER, SECRET))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys[0].kty").value("EC"));
    }

    @Test
    void invalidEnvelopeIsAnsweredWith400AndKeptInTheInbox() throws Exception {
        mvc.perform(receive("{\"header\":{}}", s.customerBpnl))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PAYLOAD_SCHEMA_VIOLATION"))
                .andExpect(jsonPath("$.errors").isNotEmpty());
        Integer stored = jdbc.queryForObject(
                "select count(*) from ex_message where partner_bpnl = ? and error_code = 'INVALID'",
                Integer.class,
                s.customerBpnl);
        assertThat(stored).isEqualTo(1);
    }

    @Test
    void receivedRequestCreatesOneCaseAndDuplicatesHaveNoEffect() throws Exception {
        UUID exchangeId = UUID.randomUUID();
        String body = requestEnvelope(exchangeId, s.customerNumberAtSupplier, s.customerBpnl, UUID.randomUUID());

        mvc.perform(receive(body, s.customerBpnl))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
        mvc.perform(receive(body, s.customerBpnl))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DUPLICATE"));

        JsonNode received = awaitCase(exchangeId);
        assertThat(received.get("source").asString()).isEqualTo("EXCHANGE");
        assertThat(received.get("status").asString()).isEqualTo("REQUESTED");
        assertThat(received.get("part").get("id").asString()).isEqualTo(s.supplierPartId.toString());
        assertThat(received.get("clarification").isNull()).isTrue();
        String caseId = received.get("id").asString();
        mvc.perform(get("/api/v1/approval-cases/" + caseId).with(supplier()))
                .andExpect(jsonPath("$.timeline[0].actor").value("partner:" + s.customerBpnl));
        Integer cases = jdbc.queryForObject(
                "select count(*) from ap_case where exchange_id = ? and own_role = 'SUPPLIER'",
                Integer.class,
                exchangeId);
        assertThat(cases).isEqualTo(1);

        // exchange record of the case: RECEIPT, VALIDATION, PROCESSING
        mvc.perform(get("/api/v1/approval-cases/" + caseId + "/exchange").with(supplier()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].direction").value("INBOUND"))
                .andExpect(jsonPath("$[0].status").value("PROCESSED"))
                .andExpect(jsonPath("$[0].caseNumber")
                        .value(received.get("caseNumber").asString()))
                .andExpect(jsonPath("$[0].steps[*].step")
                        .value(org.hamcrest.Matchers.contains("RECEIPT", "VALIDATION", "PROCESSING")));

        // withdrawal of the customer cancels the case
        mvc.perform(receive(
                        envelope(
                                MessageType.PPAP_REQUEST_WITHDRAWN,
                                codec.encodeWithdrawal(new com.msg.ppap.semantics.api.WithdrawalMessage(
                                        exchangeId, "Teil entfällt", now(), "oem.quality")),
                                exchangeId,
                                s.customerBpnl,
                                UUID.randomUUID()),
                        s.customerBpnl))
                .andExpect(status().isAccepted());
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> mvc.perform(
                                get("/api/v1/approval-cases/" + caseId).with(supplier()))
                        .andExpect(jsonPath("$.status").value("CANCELLED")));
    }

    @Test
    void unmappedPartGoesToTheClarificationListAndCanBeAssigned() throws Exception {
        UUID exchangeId = UUID.randomUUID();
        String unknownNumber = "UNMAPPED-" + exchangeId.toString().substring(0, 8);
        mvc.perform(receive(
                        requestEnvelope(exchangeId, unknownNumber, s.customerBpnl, UUID.randomUUID()), s.customerBpnl))
                .andExpect(status().isAccepted());
        JsonNode received = awaitCase(exchangeId);
        String caseId = received.get("id").asString();
        assertThat(received.get("clarification").get("reason").asString()).isEqualTo("PART_NOT_MAPPED");
        assertThat(received.get("clarification").get("partnerPartNumber").asString())
                .isEqualTo(unknownNumber);
        assertThat(received.get("part").isNull()).isTrue();

        mvc.perform(get("/api/v1/approval-cases?needsClarification=true&size=200")
                        .with(supplier()))
                .andExpect(jsonPath("$.content[*].id").value(hasItem(caseId)))
                .andExpect(jsonPath("$.content[?(@.id == '" + caseId + "')].allowedActions[*]")
                        .value(hasItem("ASSIGN_PART")));
        mvc.perform(get("/api/v1/approval-cases/stats").with(supplier()))
                .andExpect(jsonPath("$.needsClarification").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
        mvc.perform(post("/api/v1/approval-cases/" + caseId + "/start-work").with(supplier()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CLARIFICATION_PENDING"));

        UUID partId = UUID.fromString(s.create(
                        "/api/v1/parts",
                        Map.of("partNumber", "T1-" + exchangeId.toString().substring(0, 8), "nameDe", "Neu"))
                .get("id")
                .asString());
        long version = received.get("version").asLong();
        mvc.perform(post("/api/v1/approval-cases/" + caseId + "/assign-part")
                        .with(supplier())
                        .header("If-Match", "\"" + (version + 5) + "\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("partId", partId, "saveMapping", true))))
                .andExpect(status().isPreconditionFailed());
        mvc.perform(post("/api/v1/approval-cases/" + caseId + "/assign-part")
                        .with(supplier())
                        .header("If-Match", "\"" + version + "\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("partId", partId, "saveMapping", true))))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"" + (version + 1) + "\""))
                .andExpect(jsonPath("$.clarification").doesNotExist())
                .andExpect(jsonPath("$.part.id").value(partId.toString()))
                .andExpect(jsonPath("$.allowedActions").value(hasItem("START_WORK")));
        mvc.perform(get("/api/v1/part-mappings/resolve")
                        .param("partnerBpnl", s.customerBpnl)
                        .param("partnerRole", "CUSTOMER")
                        .param("partnerPartNumber", unknownNumber)
                        .with(supplier()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partId").value(partId.toString()));
    }

    @Test
    void rejectsUnknownSendersSenderMismatchAndInvalidStates() throws Exception {
        String stranger =
                "BPNL0000000X" + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        String unknown = requestEnvelope(UUID.randomUUID(), s.customerNumberAtSupplier, stranger, UUID.randomUUID());
        mvc.perform(receive(unknown, stranger)).andExpect(status().isAccepted());
        awaitRejection(stranger, "REJECTED_UNKNOWN_PARTNER");

        UUID mismatchId = UUID.randomUUID();
        mvc.perform(receive(
                        requestEnvelope(UUID.randomUUID(), s.customerNumberAtSupplier, s.customerBpnl, mismatchId),
                        "BPNL00000000OEM9"))
                .andExpect(status().isAccepted());
        awaitMessage(mismatchId, "REJECTED_SENDER_MISMATCH");

        UUID statusId = UUID.randomUUID();
        var update = new StatusUpdateMessage(UUID.randomUUID(), StatusUpdateMessage.IN_REVIEW, 1, now(), "oem.quality");
        mvc.perform(receive(
                        envelope(
                                MessageType.PPAP_STATUS_UPDATE,
                                codec.encodeStatusUpdate(update),
                                update.exchangeId(),
                                s.customerBpnl,
                                statusId),
                        s.customerBpnl))
                .andExpect(status().isAccepted());
        String id = awaitMessage(statusId, "REJECTED_INVALID_STATE");

        // cockpit: list, detail, retry, status (CONNECTOR_ADMIN)
        mvc.perform(get("/api/v1/exchange/messages?direction=INBOUND&status=REJECTED&size=200")
                        .with(supplier()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/exchange/messages?direction=INBOUND&status=REJECTED&size=200")
                        .with(TestJwts.admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id").value(hasItem(id)));
        mvc.perform(get("/api/v1/exchange/messages/" + id).with(TestJwts.admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.header.messageId").value(statusId.toString()))
                .andExpect(jsonPath("$.record.retryable").value(true));
        mvc.perform(post("/api/v1/exchange/messages/" + id + "/retry").with(TestJwts.admin()))
                .andExpect(status().isOk());
        awaitMessage(statusId, "REJECTED_INVALID_STATE");
        mvc.perform(get("/api/v1/exchange/status").with(TestJwts.admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.edc").value("DOWN"))
                .andExpect(jsonPath("$.inbox.rejected").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    @Test
    void sendingWritesTheOutboxAndRetriesWhileTheEdcIsDown() throws Exception {
        JsonNode draft = json.readTree(mvc.perform(post("/api/v1/approval-cases")
                        .with(engineer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "partId", s.customerPartId,
                                "reasonCode", s.reason,
                                "levelCode", s.level,
                                "dueDate", LocalDate.now().plusDays(20).toString()))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8));
        String id = draft.get("id").asString();
        mvc.perform(post("/api/v1/approval-cases/" + id + "/send").with(engineer()))
                .andExpect(status().isOk());

        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> mvc.perform(get("/api/v1/approval-cases/" + id + "/exchange")
                                .with(engineer()))
                        .andExpect(jsonPath("$[0].direction").value("OUTBOUND"))
                        .andExpect(jsonPath("$[0].messageType").value("PPAP_REQUEST"))
                        .andExpect(jsonPath("$[0].status").value("PENDING"))
                        .andExpect(jsonPath("$[0].attempts").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                        .andExpect(jsonPath("$[0].errorCode").value(org.hamcrest.Matchers.startsWith("EDC_")))
                        .andExpect(jsonPath("$[0].nextAttemptAt").isNotEmpty()));
        mvc.perform(get("/api/v1/approval-cases/" + id).with(engineer()))
                .andExpect(jsonPath("$.lastOutbound.messageType").value("PPAP_REQUEST"))
                .andExpect(
                        jsonPath("$.lastOutbound.status").value(org.hamcrest.Matchers.oneOf("PENDING", "IN_PROGRESS")));
        mvc.perform(get("/api/v1/approval-cases?size=200").with(engineer()))
                .andExpect(jsonPath("$.content[?(@.id == '" + id + "')].lastOutbound.messageType")
                        .value(hasItem("PPAP_REQUEST")));
        String envelope = jdbc.queryForObject(
                "select envelope from ex_message where case_id = ?::uuid and direction = 'OUTBOUND'", String.class, id);
        Notification n = notifications.decode(envelope.getBytes(StandardCharsets.UTF_8));
        assertThat(n.header().senderBpn()).isEqualTo(ApprovalScenario.TENANT);
        assertThat(n.header().receiverBpn()).isEqualTo(s.supplierBpnl);
        assertThat(n.payloadHashValid()).isTrue();
        assertThat(codec.decodeRequest(n.payload()).message().customerPartNumber())
                .isEqualTo(s.customerPartNumber);
        // failed outbound messages are not retryable before they are given up
        String messageRef = jdbc.queryForObject(
                "select id::text from ex_message where case_id = ?::uuid and direction = 'OUTBOUND'", String.class, id);
        mvc.perform(post("/api/v1/exchange/messages/" + messageRef + "/retry").with(TestJwts.admin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MESSAGE_NOT_RETRYABLE"));
    }

    @Test
    void liveEventStreamIsOpenedForAuthenticatedUsers() throws Exception {
        mvc.perform(get("/api/v1/events")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/events").with(engineer()).accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted());
    }

    // ---------------------------------------------------------------------------------------------------------

    MockHttpServletRequestBuilder receive(String body, String edcBpn) {
        return post(RECEIVE)
                .header(SECRET_HEADER, SECRET)
                .header("Edc-Bpn", edcBpn)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.getBytes(StandardCharsets.UTF_8));
    }

    String requestEnvelope(UUID exchangeId, String customerPartNumber, String sender, UUID messageId) {
        var request = new RequestMessage(
                exchangeId,
                "PPF-2026-90001",
                sender,
                ApprovalScenario.TENANT,
                customerPartNumber,
                null,
                "Türgriff",
                new RequestMessage.CodeName("NEW_PART", "Neuteil", "New part"),
                new RequestMessage.CodeName("VDA-2", "Stufe 2", "Level 2"),
                LocalDate.now().plusDays(30),
                null,
                now(),
                "oem.quality",
                List.of(new RequestMessage.Element(
                        s.docElement,
                        "REQUIRED",
                        null,
                        new RequestMessage.Snapshot("Deckblatt", "Cover", "DOCUMENT", null))),
                List.of());
        return envelope(MessageType.PPAP_REQUEST, codec.encodeRequest(request), exchangeId, sender, messageId);
    }

    String envelope(MessageType type, EncodedPayload payload, UUID exchangeId, String sender, UUID messageId) {
        var notification = new Notification(
                new Notification.Header(
                        messageId, type.modelUrn(), now(), sender, ApprovalScenario.TENANT, null, null, null),
                type,
                exchangeId,
                "test-" + messageId,
                payload.canonical(),
                payload.hash(),
                null);
        return new String(notifications.encode(notification), StandardCharsets.UTF_8);
    }

    JsonNode awaitCase(UUID exchangeId) {
        return await().atMost(Duration.ofSeconds(15))
                .until(
                        () -> {
                            JsonNode page =
                                    json.readTree(mvc.perform(get("/api/v1/approval-cases?ownRole=SUPPLIER&size=200")
                                                    .with(supplier()))
                                            .andReturn()
                                            .getResponse()
                                            .getContentAsString(StandardCharsets.UTF_8));
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

    void awaitRejection(String sender, String code) {
        await().atMost(Duration.ofSeconds(15))
                .until(() -> jdbc.queryForObject(
                                "select count(*) from ex_message where partner_bpnl = ? and error_code = ?",
                                Integer.class,
                                sender,
                                code)
                        == 1);
    }

    String awaitMessage(UUID messageId, String code) {
        await().atMost(Duration.ofSeconds(15))
                .until(() -> jdbc.queryForObject(
                                "select count(*) from ex_message where message_id = ? and status = 'REJECTED' and error_code = ?",
                                Integer.class,
                                messageId,
                                code)
                        == 1);
        return jdbc.queryForObject("select id::text from ex_message where message_id = ?", String.class, messageId);
    }

    static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }
}
