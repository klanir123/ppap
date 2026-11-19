package com.msg.ppap.approval.domain;

/** Verification status of a payload signature. */
public enum SignatureStatus {
    /** Signature verified against the signer's public key. */
    VALID,
    /** Signature does not match the payload. */
    INVALID,
    /** Signer key not resolvable yet (partner keys: phase 4). */
    UNVERIFIED,
    /** Unsigned (legacy import). */
    NOT_SIGNED
}
