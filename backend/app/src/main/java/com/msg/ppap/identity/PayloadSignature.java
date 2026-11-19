package com.msg.ppap.identity;

import java.util.Objects;

/**
 * Detached payload signature.
 *
 * @param alg JWS algorithm, {@code ES256}
 * @param kid key id: RFC 7638 JWK thumbprint (SHA-256, base64url) of the public key
 * @param jws detached JWS compact serialization {@code <header>..<signature>} with unencoded payload (RFC 7797,
 *     {@code "b64": false})
 */
public record PayloadSignature(String alg, String kid, String jws) {

    public PayloadSignature {
        Objects.requireNonNull(alg, "alg");
        Objects.requireNonNull(kid, "kid");
        Objects.requireNonNull(jws, "jws");
    }
}
