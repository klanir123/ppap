package com.msg.ppap.approval.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Canonical exchange payload of a submitted revision or decision with its hash and signature (immutable).
 *
 * @param payload canonical JSON (RFC 8785)
 * @param payloadHash {@code sha256:<hex>} over the UTF-8 bytes of {@code payload}
 * @param signature signature over the payload bytes
 */
public record Seal(String payload, String payloadHash, SignatureInfo signature) {

    public Seal {
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(payloadHash, "payloadHash");
        Objects.requireNonNull(signature, "signature");
    }

    /** Payload bytes. */
    public byte[] payloadBytes() {
        return payload.getBytes(StandardCharsets.UTF_8);
    }

    /** Whether the stored hash matches the stored payload. */
    public boolean hashValid() {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(payloadBytes());
            return payloadHash.equals("sha256:" + HexFormat.of().formatHex(digest));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    Seal withSignatureStatus(SignatureStatus status) {
        return new Seal(payload, payloadHash, signature.withStatus(status));
    }
}
