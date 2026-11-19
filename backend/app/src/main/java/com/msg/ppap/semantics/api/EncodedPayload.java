package com.msg.ppap.semantics.api;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * A validated exchange payload in canonical form (RFC 8785).
 *
 * @param modelUrn semantic model of the payload, e.g. {@code urn:samm:com.msg.ppap:0.1.0#PpapSubmission}
 * @param canonical canonical UTF-8 bytes – the bytes that are hashed, signed, stored and transferred
 * @param hash {@code sha256:<hex>} over {@code canonical}
 */
public record EncodedPayload(String modelUrn, byte[] canonical, String hash) {

    public EncodedPayload {
        Objects.requireNonNull(modelUrn, "modelUrn");
        Objects.requireNonNull(canonical, "canonical");
        Objects.requireNonNull(hash, "hash");
        canonical = canonical.clone();
    }

    @Override
    public byte[] canonical() {
        return canonical.clone();
    }

    /** Canonical JSON text. */
    public String text() {
        return new String(canonical, StandardCharsets.UTF_8);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof EncodedPayload that && hash.equals(that.hash) && modelUrn.equals(that.modelUrn);
    }

    @Override
    public int hashCode() {
        return hash.hashCode();
    }

    @Override
    public String toString() {
        return "EncodedPayload[" + modelUrn + ", " + hash + "]";
    }
}
