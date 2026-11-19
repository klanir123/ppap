package com.msg.ppap.semantics.api;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Envelope {@code PpapNotification} of every message exchanged via the EDC data plane (docs/api/exchange-api.md).
 * The header follows {@code urn:samm:io.catenax.shared.message_header:3.0.0#MessageHeaderAspect}.
 *
 * @param header message header
 * @param messageType one of {@link MessageType}
 * @param exchangeId exchange id shared by both cases
 * @param correlationId correlation id (logging, tracing across participants)
 * @param payload canonical (RFC 8785) payload bytes
 * @param payloadHash {@code sha256:<hex>} over {@code payload} as claimed by the sender
 * @param signature detached JWS over {@code payload} ({@code null} for unsigned message types)
 */
public record Notification(
        Header header,
        MessageType messageType,
        UUID exchangeId,
        String correlationId,
        byte[] payload,
        String payloadHash,
        String signature) {

    /** Version of the reused Catena-X message header model. */
    public static final String HEADER_VERSION = "3.0.0";

    public Notification {
        Objects.requireNonNull(header, "header");
        Objects.requireNonNull(messageType, "messageType");
        Objects.requireNonNull(exchangeId, "exchangeId");
        Objects.requireNonNull(correlationId, "correlationId");
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(payloadHash, "payloadHash");
        payload = payload.clone();
    }

    @Override
    public byte[] payload() {
        return payload.clone();
    }

    /** Payload as canonical JSON text. */
    public String payloadText() {
        return new String(payload, StandardCharsets.UTF_8);
    }

    /** Whether the claimed hash matches the canonical payload. */
    public boolean payloadHashValid() {
        return PayloadHash.of(payload).equals(payloadHash);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Notification that && header.equals(that.header) && payloadHash.equals(that.payloadHash);
    }

    @Override
    public int hashCode() {
        return header.hashCode();
    }

    @Override
    public String toString() {
        return "Notification[" + messageType + ", " + header.messageId() + ", " + payloadHash + "]";
    }

    /**
     * Message header (Catena-X message header 3.0.0).
     *
     * @param context model URN of the payload
     * @param expectedResponseBy optional
     * @param relatedMessageId optional reference to a previous message
     */
    public record Header(
            UUID messageId,
            String context,
            Instant sentDateTime,
            String senderBpn,
            String receiverBpn,
            Instant expectedResponseBy,
            UUID relatedMessageId,
            String version) {

        public Header {
            Objects.requireNonNull(messageId, "messageId");
            Objects.requireNonNull(context, "context");
            Objects.requireNonNull(sentDateTime, "sentDateTime");
            Objects.requireNonNull(senderBpn, "senderBpn");
            Objects.requireNonNull(receiverBpn, "receiverBpn");
            version = version == null ? HEADER_VERSION : version;
        }
    }

    /** Message types and the model of their payload. */
    public enum MessageType {
        PPAP_REQUEST(ExchangeModels.PPAP_REQUEST, false),
        PPAP_REQUEST_WITHDRAWN(ExchangeModels.PPAP_REQUEST_WITHDRAWN, false),
        PPAP_STATUS_UPDATE(ExchangeModels.PPAP_STATUS_UPDATE, false),
        PPAP_SUBMISSION(ExchangeModels.PPAP_SUBMISSION, true),
        /** Phase 5: submitted revision available for pull via the supplier's DTR (unsigned, carries the hash). */
        PPAP_SUBMISSION_AVAILABLE(ExchangeModels.PPAP_SUBMISSION_AVAILABLE, false),
        PPAP_DECISION(ExchangeModels.PPAP_DECISION, true);

        private final String modelUrn;
        private final boolean signed;

        MessageType(String modelUrn, boolean signed) {
            this.modelUrn = modelUrn;
            this.signed = signed;
        }

        /** Model URN of the payload ({@code header.context}). */
        public String modelUrn() {
            return modelUrn;
        }

        /** Whether the payload carries a detached JWS. */
        public boolean signed() {
            return signed;
        }
    }
}
