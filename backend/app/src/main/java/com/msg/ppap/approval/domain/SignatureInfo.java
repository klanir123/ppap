package com.msg.ppap.approval.domain;

import java.util.Objects;

/**
 * Detached JWS signature of a payload and its verification status.
 *
 * @param alg algorithm ({@code ES256}), {@code null} if not signed
 * @param kid key id, {@code null} if not signed
 * @param jws detached compact JWS, {@code null} if not signed
 */
public record SignatureInfo(String alg, String kid, String jws, SignatureStatus status) {

    public static final SignatureInfo NOT_SIGNED = new SignatureInfo(null, null, null, SignatureStatus.NOT_SIGNED);

    public SignatureInfo {
        Objects.requireNonNull(status, "status");
    }

    SignatureInfo withStatus(SignatureStatus newStatus) {
        return new SignatureInfo(alg, kid, jws, newStatus);
    }
}
