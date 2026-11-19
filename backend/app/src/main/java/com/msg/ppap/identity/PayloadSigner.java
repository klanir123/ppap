package com.msg.ppap.identity;

/**
 * Signs canonical exchange payloads with the tenant key (ADR-0008). The signature is a detached JWS so that the
 * payload is stored and transferred exactly once, unmodified. Later implementations may sign with verifiable
 * credentials of the participant DID without changes to callers (AK-07-3).
 */
public interface PayloadSigner {

    /**
     * Signs the given canonical bytes.
     *
     * @param canonicalPayload RFC 8785 canonical UTF-8 bytes
     * @return detached signature
     */
    PayloadSignature sign(byte[] canonicalPayload);
}
