package com.msg.ppap.semantics.api;

/**
 * Encodes and decodes the {@code PpapNotification} envelope. Envelope and payload are validated against their JSON
 * Schemas on both sides; the payload is embedded as JSON object and always handled in its RFC 8785 canonical form,
 * so the receiver can recompute the hash over exactly the bytes the sender hashed and signed.
 */
public interface NotificationCodec {

    /**
     * Serializes the envelope (UTF-8 JSON).
     *
     * @throws PayloadValidationException if envelope or payload violate their schemas
     */
    byte[] encode(Notification notification);

    /**
     * Parses and validates a received envelope including its payload. The claimed {@code payloadHash} is
     * <em>not</em> checked here (see {@link Notification#payloadHashValid()}).
     *
     * @throws PayloadValidationException if the body is not valid JSON or envelope or payload violate their schemas
     */
    Notification decode(byte[] body);
}
