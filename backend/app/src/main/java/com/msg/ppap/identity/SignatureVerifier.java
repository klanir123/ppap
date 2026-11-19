package com.msg.ppap.identity;

/** Verifies detached payload signatures (see {@link PayloadSigner}). */
public interface SignatureVerifier {

    /** Outcome of a verification. */
    enum Result {
        /** Signature matches the payload and a known key. */
        VALID,
        /** Malformed JWS, wrong algorithm or signature does not match the payload. */
        INVALID,
        /** Signed with a key that cannot be resolved (neither own nor a known key of the signer). */
        UNKNOWN_KEY
    }

    /**
     * Verifies the detached JWS over the canonical payload bytes.
     *
     * @param canonicalPayload canonical UTF-8 bytes as received/stored
     * @param jws detached compact JWS
     */
    Result verify(byte[] canonicalPayload, String jws);

    /**
     * Verifies the detached JWS of a partner: the key is looked up among the known keys of the signer ({@link
     * PartnerKeys}); the own tenant key is accepted only if {@code signerBpnl} is {@code null}.
     *
     * @param signerBpnl BPNL of the expected signer, {@code null} for own payloads
     */
    Result verify(byte[] canonicalPayload, String jws, String signerBpnl);

    /** Key id ({@code kid}) of the JWS header, or {@code null} if unparsable. */
    String keyId(String jws);
}
