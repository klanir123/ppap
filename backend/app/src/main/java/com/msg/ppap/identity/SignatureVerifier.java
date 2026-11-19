package com.msg.ppap.identity;

/** Verifies detached payload signatures (see {@link PayloadSigner}). */
public interface SignatureVerifier {

    /** Outcome of a verification. */
    enum Result {
        /** Signature matches the payload and a known key. */
        VALID,
        /** Malformed JWS, wrong algorithm or signature does not match the payload. */
        INVALID,
        /** Signed with a key that cannot be resolved (yet). TODO(phase-4): resolve partner keys via DID. */
        UNKNOWN_KEY
    }

    /**
     * Verifies the detached JWS over the canonical payload bytes.
     *
     * @param canonicalPayload canonical UTF-8 bytes as received/stored
     * @param jws detached compact JWS
     */
    Result verify(byte[] canonicalPayload, String jws);

    /** Key id ({@code kid}) of the JWS header, or {@code null} if unparsable. */
    String keyId(String jws);
}
