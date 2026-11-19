package com.msg.ppap.exchange.domain;

import java.util.Map;

/**
 * Rejection codes of inbound messages (inbox status {@code REJECTED}) and their derivation from the error codes of
 * the processing modules.
 */
public enum InboundRejection {
    /** Envelope or payload violate the schema (answered with 400). */
    INVALID,
    /** Sender has no active relation. */
    REJECTED_UNKNOWN_PARTNER,
    /** Sender in the header does not match the consumer identity forwarded by the data plane or the case partner. */
    REJECTED_SENDER_MISMATCH,
    /** Message addressed to another participant. */
    REJECTED_WRONG_RECIPIENT,
    /** Claimed payload hash does not match the payload. */
    REJECTED_PAYLOAD_HASH_MISMATCH,
    /** Signature invalid. */
    REJECTED_SIGNATURE_INVALID,
    /** Transition not allowed in the current state, unknown case or revision conflict. */
    REJECTED_INVALID_STATE,
    /** Unexpected technical failure during processing (manual retry possible). */
    PROCESSING_FAILED;

    private static final Map<String, InboundRejection> BY_ERROR_CODE = Map.ofEntries(
            Map.entry("PAYLOAD_SCHEMA_VIOLATION", INVALID),
            Map.entry("UNKNOWN_SENDER", REJECTED_SENDER_MISMATCH),
            Map.entry("WRONG_RECIPIENT", REJECTED_WRONG_RECIPIENT),
            Map.entry("PAYLOAD_HASH_MISMATCH", REJECTED_PAYLOAD_HASH_MISMATCH),
            Map.entry("SIGNATURE_INVALID", REJECTED_SIGNATURE_INVALID),
            Map.entry("INVALID_STATE_TRANSITION", REJECTED_INVALID_STATE),
            Map.entry("WRONG_CASE_ROLE", REJECTED_INVALID_STATE),
            Map.entry("REVISION_CONFLICT", REJECTED_INVALID_STATE),
            Map.entry("NOT_FOUND", REJECTED_INVALID_STATE),
            Map.entry("NO_ELEMENTS", INVALID));

    /** Rejection for an error code of the approval/semantics modules; unknown codes are processing failures. */
    public static InboundRejection fromErrorCode(String errorCode) {
        return errorCode == null ? PROCESSING_FAILED : BY_ERROR_CODE.getOrDefault(errorCode, PROCESSING_FAILED);
    }
}
