package com.msg.ppap.semantics.api;

/**
 * Encodes version-neutral exchange messages into canonical payloads of the current exchange format (validated
 * against its JSON Schema, canonicalized per RFC 8785, hashed) and decodes received payloads.
 *
 * <p>The approval domain never sees the payload structure: the approval application maps its aggregate to these
 * messages. Replacing the format (e.g. by SAMM-generated schemas of the Catena-X standard) means adding a mapper
 * version behind this interface.
 */
public interface ExchangeCodec {

    /** @throws PayloadValidationException if the resulting payload violates the schema */
    EncodedPayload encodeRequest(RequestMessage message);

    /** @throws PayloadValidationException if the resulting payload violates the schema */
    EncodedPayload encodeSubmission(SubmissionMessage message);

    /** @throws PayloadValidationException if the resulting payload violates the schema */
    EncodedPayload encodeDecision(DecisionMessage message);

    /** @throws PayloadValidationException if the resulting payload violates the schema */
    EncodedPayload encodeStatusUpdate(StatusUpdateMessage message);

    /** @throws PayloadValidationException if the resulting payload violates the schema */
    EncodedPayload encodeWithdrawal(WithdrawalMessage message);

    /** @throws PayloadValidationException if the resulting payload violates the schema */
    EncodedPayload encodeSubmissionAvailable(SubmissionAvailableMessage message);

    /** @throws PayloadValidationException if the payload is not valid JSON or violates the schema */
    Decoded<SubmissionAvailableMessage> decodeSubmissionAvailable(byte[] payload);

    /** @throws PayloadValidationException if the payload is not valid JSON or violates the schema */
    Decoded<RequestMessage> decodeRequest(byte[] payload);

    /** @throws PayloadValidationException if the payload is not valid JSON or violates the schema */
    Decoded<StatusUpdateMessage> decodeStatusUpdate(byte[] payload);

    /** @throws PayloadValidationException if the payload is not valid JSON or violates the schema */
    Decoded<WithdrawalMessage> decodeWithdrawal(byte[] payload);

    /** @throws PayloadValidationException if the payload is not valid JSON or violates the schema */
    Decoded<SubmissionMessage> decodeSubmission(byte[] payload);

    /** @throws PayloadValidationException if the payload is not valid JSON or violates the schema */
    Decoded<DecisionMessage> decodeDecision(byte[] payload);

    /**
     * Result of decoding a received payload.
     *
     * @param message decoded message
     * @param payload canonical form of the received payload (hash over the canonical bytes)
     * @param <T> message type
     */
    record Decoded<T>(T message, EncodedPayload payload) {}
}
