package com.msg.ppap.semantics.application;

import com.msg.ppap.platform.domain.FieldViolation;
import com.msg.ppap.semantics.api.CanonicalJson;
import com.msg.ppap.semantics.api.DecisionMessage;
import com.msg.ppap.semantics.api.EncodedPayload;
import com.msg.ppap.semantics.api.ExchangeCodec;
import com.msg.ppap.semantics.api.ExchangeModels;
import com.msg.ppap.semantics.api.PayloadHash;
import com.msg.ppap.semantics.api.PayloadValidationException;
import com.msg.ppap.semantics.api.RequestMessage;
import com.msg.ppap.semantics.api.StatusUpdateMessage;
import com.msg.ppap.semantics.api.SubmissionMessage;
import com.msg.ppap.semantics.api.WithdrawalMessage;
import com.msg.ppap.semantics.application.port.out.PayloadSchemaValidator;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@link ExchangeCodec} for exchange format v0.1: map → validate (JSON Schema) → canonicalize (RFC 8785) → hash
 * (SHA-256). Received payloads are parsed strictly (duplicate keys rejected), validated and canonicalized; the hash
 * is always computed over the canonical form.
 */
@Service
public class ExchangeCodecService implements ExchangeCodec {

    private static final JsonMapper STRICT = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(tools.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build();

    private final ExchangeMapper mapper;
    private final PayloadSchemaValidator validator;

    public ExchangeCodecService(ExchangeMapper mapper, PayloadSchemaValidator validator) {
        this.mapper = mapper;
        this.validator = validator;
    }

    @Override
    public EncodedPayload encodeRequest(RequestMessage message) {
        return encode(ExchangeModels.PPAP_REQUEST, mapper.toPayload(message));
    }

    @Override
    public EncodedPayload encodeSubmission(SubmissionMessage message) {
        return encode(ExchangeModels.PPAP_SUBMISSION, mapper.toPayload(message));
    }

    @Override
    public EncodedPayload encodeDecision(DecisionMessage message) {
        return encode(ExchangeModels.PPAP_DECISION, mapper.toPayload(message));
    }

    @Override
    public EncodedPayload encodeStatusUpdate(StatusUpdateMessage message) {
        return encode(ExchangeModels.PPAP_STATUS_UPDATE, mapper.toPayload(message));
    }

    @Override
    public EncodedPayload encodeWithdrawal(WithdrawalMessage message) {
        return encode(ExchangeModels.PPAP_REQUEST_WITHDRAWN, mapper.toPayload(message));
    }

    @Override
    public Decoded<StatusUpdateMessage> decodeStatusUpdate(byte[] payload) {
        return decode(ExchangeModels.PPAP_STATUS_UPDATE, payload, mapper::toStatusUpdate);
    }

    @Override
    public Decoded<WithdrawalMessage> decodeWithdrawal(byte[] payload) {
        return decode(ExchangeModels.PPAP_REQUEST_WITHDRAWN, payload, mapper::toWithdrawal);
    }

    @Override
    public Decoded<RequestMessage> decodeRequest(byte[] payload) {
        return decode(ExchangeModels.PPAP_REQUEST, payload, mapper::toRequest);
    }

    @Override
    public Decoded<SubmissionMessage> decodeSubmission(byte[] payload) {
        return decode(ExchangeModels.PPAP_SUBMISSION, payload, mapper::toSubmission);
    }

    @Override
    public Decoded<DecisionMessage> decodeDecision(byte[] payload) {
        return decode(ExchangeModels.PPAP_DECISION, payload, mapper::toDecision);
    }

    private EncodedPayload encode(String modelUrn, JsonNode node) {
        validate(modelUrn, node);
        byte[] canonical = CanonicalJson.canonicalizeJson(node.toString().getBytes(StandardCharsets.UTF_8));
        return new EncodedPayload(modelUrn, canonical, PayloadHash.of(canonical));
    }

    private <T> Decoded<T> decode(String modelUrn, byte[] payload, Function<JsonNode, T> toMessage) {
        JsonNode node;
        try {
            node = STRICT.readTree(payload);
        } catch (JacksonException ex) {
            throw new PayloadValidationException(
                    List.of(new FieldViolation("", "INVALID_JSON", "payload is not valid JSON")));
        }
        if (node == null || !node.isObject()) {
            throw new PayloadValidationException(
                    List.of(new FieldViolation("", "INVALID_JSON", "payload must be a JSON object")));
        }
        EncodedPayload encoded = encode(modelUrn, node);
        return new Decoded<>(toMessage.apply(node), encoded);
    }

    private void validate(String modelUrn, JsonNode node) {
        List<FieldViolation> violations = validator.validate(modelUrn, node);
        if (!violations.isEmpty()) {
            throw new PayloadValidationException(violations);
        }
    }
}
