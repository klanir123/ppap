package com.msg.ppap.semantics.application;

import com.msg.ppap.platform.domain.FieldViolation;
import com.msg.ppap.semantics.api.CanonicalJson;
import com.msg.ppap.semantics.api.ExchangeModels;
import com.msg.ppap.semantics.api.Notification;
import com.msg.ppap.semantics.api.NotificationCodec;
import com.msg.ppap.semantics.api.PayloadValidationException;
import com.msg.ppap.semantics.application.port.out.PayloadSchemaValidator;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * {@link NotificationCodec} of exchange format v0.1 (SAMM model {@code PpapNotification}). The payload is embedded as
 * its RFC 8785 canonical JSON text (SAMM has no type for "one of several aspects"), i.e. exactly the bytes that were
 * hashed and signed; the envelope itself is serialized canonically as well.
 */
@Service
public class NotificationCodecService implements NotificationCodec {

    private static final JsonMapper STRICT = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build();

    private final PayloadSchemaValidator validator;

    public NotificationCodecService(PayloadSchemaValidator validator) {
        this.validator = validator;
    }

    @Override
    public byte[] encode(Notification n) {
        JsonNode payload = parse(n.payload(), "/content/payload");
        validate(n.messageType().modelUrn(), payload, "/content/payload");
        ObjectNode envelope = STRICT.createObjectNode();
        ObjectNode header = envelope.putObject("header");
        Notification.Header h = n.header();
        header.put("messageId", h.messageId().toString());
        header.put("context", h.context());
        header.put("sentDateTime", h.sentDateTime().toString());
        header.put("senderBpn", h.senderBpn());
        header.put("receiverBpn", h.receiverBpn());
        putOptional(header, "expectedResponseBy", h.expectedResponseBy());
        putOptional(header, "relatedMessageId", h.relatedMessageId());
        header.put("version", h.version());
        ObjectNode content = envelope.putObject("content");
        content.put("messageType", n.messageType().name());
        content.put("exchangeId", n.exchangeId().toString());
        content.put("correlationId", n.correlationId());
        // payload as canonical JSON text: exactly the bytes that are hashed and signed (SAMM: JsonText)
        content.put("payload", CanonicalJson.canonicalizeJson(payload.toString()));
        content.put("payloadHash", n.payloadHash());
        if (n.signature() != null) {
            content.put("signature", n.signature());
        }
        validate(ExchangeModels.PPAP_NOTIFICATION, envelope, "");
        return CanonicalJson.canonicalizeJson(envelope.toString()).getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public Notification decode(byte[] body) {
        JsonNode envelope = parse(body, "");
        validate(ExchangeModels.PPAP_NOTIFICATION, envelope, "");
        JsonNode header = envelope.get("header");
        JsonNode content = envelope.get("content");
        Notification.MessageType type =
                Notification.MessageType.valueOf(content.get("messageType").asString());
        JsonNode payload =
                parse(content.get("payload").asString().getBytes(StandardCharsets.UTF_8), "/content/payload");
        validate(type.modelUrn(), payload, "/content/payload");
        if (!type.modelUrn().equals(header.get("context").asString())) {
            throw new PayloadValidationException(List.of(new FieldViolation(
                    "/header/context", "CONTEXT_MISMATCH", "context does not match message type " + type)));
        }
        byte[] canonical = CanonicalJson.canonicalizeJson(payload.toString()).getBytes(StandardCharsets.UTF_8);
        return new Notification(
                new Notification.Header(
                        UUID.fromString(header.get("messageId").asString()),
                        header.get("context").asString(),
                        Instant.parse(header.get("sentDateTime").asString()),
                        header.get("senderBpn").asString(),
                        header.get("receiverBpn").asString(),
                        instant(header.get("expectedResponseBy")),
                        uuid(header.get("relatedMessageId")),
                        header.get("version").asString()),
                type,
                UUID.fromString(content.get("exchangeId").asString()),
                content.get("correlationId").asString(),
                canonical,
                content.get("payloadHash").asString(),
                text(content.get("signature")));
    }

    private static JsonNode parse(byte[] json, String pointer) {
        JsonNode node;
        try {
            node = STRICT.readTree(json);
        } catch (JacksonException ex) {
            throw new PayloadValidationException(
                    List.of(new FieldViolation(pointer, "INVALID_JSON", "not valid JSON")));
        }
        if (node == null || !node.isObject()) {
            throw new PayloadValidationException(
                    List.of(new FieldViolation(pointer, "INVALID_JSON", "must be a JSON object")));
        }
        return node;
    }

    private void validate(String modelUrn, JsonNode node, String prefix) {
        List<FieldViolation> violations = validator.validate(modelUrn, node);
        if (!violations.isEmpty()) {
            throw new PayloadValidationException(violations.stream()
                    .map(v -> new FieldViolation(prefix + v.field(), v.code(), v.message()))
                    .toList());
        }
    }

    private static void putOptional(ObjectNode node, String name, Object value) {
        if (value != null) {
            node.put(name, value.toString());
        }
    }

    private static String text(JsonNode node) {
        return node == null || node.isNull() ? null : node.asString();
    }

    private static Instant instant(JsonNode node) {
        String value = text(node);
        return value == null ? null : Instant.parse(value);
    }

    private static UUID uuid(JsonNode node) {
        String value = text(node);
        return value == null ? null : UUID.fromString(value);
    }
}
