package com.msg.ppap.semantics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.msg.ppap.semantics.adapter.out.schema.SchemaValidatorAccess;
import com.msg.ppap.semantics.api.EncodedPayload;
import com.msg.ppap.semantics.api.ExchangeCodec;
import com.msg.ppap.semantics.api.ExchangeModels;
import com.msg.ppap.semantics.api.Notification;
import com.msg.ppap.semantics.api.Notification.MessageType;
import com.msg.ppap.semantics.api.PayloadValidationException;
import com.msg.ppap.semantics.api.StatusUpdateMessage;
import com.msg.ppap.semantics.api.WithdrawalMessage;
import com.msg.ppap.semantics.application.ExchangeCodecService;
import com.msg.ppap.semantics.application.NotificationCodecService;
import com.msg.ppap.semantics.application.v0_1.ExchangeMapperV0_1;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationCodecTest {

    static final UUID EXCHANGE_ID = UUID.fromString("4a8ca33e-609f-55f1-9a79-77031bf2c0a4");
    static final Instant AT = Instant.parse("2026-11-19T10:15:30.123Z");

    final ExchangeCodec codec = new ExchangeCodecService(new ExchangeMapperV0_1(), SchemaValidatorAccess.validator());
    final NotificationCodecService notifications = new NotificationCodecService(SchemaValidatorAccess.validator());

    @Test
    void statusUpdateAndWithdrawalRoundTrip() {
        var update = new StatusUpdateMessage(EXCHANGE_ID, StatusUpdateMessage.IN_PROGRESS, 2, AT, "tier1.quality");
        EncodedPayload encoded = codec.encodeStatusUpdate(update);
        assertThat(encoded.modelUrn()).isEqualTo(ExchangeModels.PPAP_STATUS_UPDATE);
        assertThat(codec.decodeStatusUpdate(encoded.canonical()).message()).isEqualTo(update);

        var withdrawal = new WithdrawalMessage(EXCHANGE_ID, "Teil entfällt", AT, "oem.quality");
        EncodedPayload w = codec.encodeWithdrawal(withdrawal);
        assertThat(codec.decodeWithdrawal(w.canonical()).message()).isEqualTo(withdrawal);
        assertThatThrownBy(() -> codec.encodeStatusUpdate(new StatusUpdateMessage(EXCHANGE_ID, "DONE", 1, AT, "x")))
                .isInstanceOf(PayloadValidationException.class);
    }

    @Test
    void envelopeRoundTripKeepsTheCanonicalPayload() {
        EncodedPayload payload = codec.encodeSubmission(ExchangeCodecTest.submission());
        Notification sent = notification(MessageType.PPAP_SUBMISSION, payload, "eyJhbGciOiJFUzI1NiJ9..c2ln");
        byte[] body = notifications.encode(sent);

        Notification received = notifications.decode(body);
        assertThat(received.header()).isEqualTo(sent.header());
        assertThat(received.messageType()).isEqualTo(MessageType.PPAP_SUBMISSION);
        assertThat(received.payload()).isEqualTo(payload.canonical());
        assertThat(received.payloadHashValid()).isTrue();
        assertThat(received.signature()).isEqualTo(sent.signature());
        assertThat(received.payloadText()).isEqualTo(payload.text());
        assertThat(received).isEqualTo(sent).hasSameHashCodeAs(sent);
        // the envelope is canonical as well: re-encoding yields identical bytes
        assertThat(notifications.encode(received)).isEqualTo(body);
        assertThat(MessageType.PPAP_SUBMISSION.signed()).isTrue();
        assertThat(MessageType.PPAP_REQUEST.signed()).isFalse();
    }

    @Test
    void detectsTamperedPayloadHash() {
        EncodedPayload payload = codec.encodeStatusUpdate(
                new StatusUpdateMessage(EXCHANGE_ID, StatusUpdateMessage.IN_REVIEW, 1, AT, "oem.quality"));
        String body = new String(
                        notifications.encode(notification(MessageType.PPAP_STATUS_UPDATE, payload, null)),
                        StandardCharsets.UTF_8)
                .replace("\\\"revision\\\":1", "\\\"revision\\\":3");
        Notification received = notifications.decode(body.getBytes(StandardCharsets.UTF_8));
        assertThat(received.payloadHashValid()).isFalse();
    }

    @Test
    void rejectsInvalidEnvelopes() {
        EncodedPayload payload = codec.encodeStatusUpdate(
                new StatusUpdateMessage(EXCHANGE_ID, StatusUpdateMessage.IN_REVIEW, 1, AT, "oem.quality"));
        String valid = new String(
                notifications.encode(notification(MessageType.PPAP_STATUS_UPDATE, payload, null)),
                StandardCharsets.UTF_8);
        assertThatThrownBy(() -> notifications.decode("not json".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(PayloadValidationException.class);
        assertThatThrownBy(() -> notifications.decode("[1]".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(PayloadValidationException.class);
        assertThatThrownBy(() -> notifications.decode(
                        valid.replace("BPNL000000TIER01", "nobody").getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(PayloadValidationException.class)
                .satisfies(ex -> assertThat(((PayloadValidationException) ex).violations())
                        .anyMatch(v -> v.field().startsWith("/header")));
        // payload violates its own schema
        assertThatThrownBy(() ->
                        notifications.decode(valid.replace("IN_REVIEW", "DONE").getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(PayloadValidationException.class)
                .satisfies(ex -> assertThat(((PayloadValidationException) ex).violations())
                        .anyMatch(v -> v.field().startsWith("/content/payload")));
        // context does not fit the message type
        assertThatThrownBy(() -> notifications.decode(
                        valid.replace("#PpapStatusUpdate", "#PpapDecision").getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(PayloadValidationException.class);
    }

    static Notification notification(MessageType type, EncodedPayload payload, String signature) {
        return new Notification(
                new Notification.Header(
                        UUID.fromString("0efb5332-4273-452f-ba10-a75670c165fe"),
                        type.modelUrn(),
                        AT,
                        "BPNL00000003AYRE",
                        "BPNL000000TIER01",
                        null,
                        null,
                        null),
                type,
                EXCHANGE_ID,
                "corr-1",
                payload.canonical(),
                payload.hash(),
                signature);
    }
}
