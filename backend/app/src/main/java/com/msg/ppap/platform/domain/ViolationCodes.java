package com.msg.ppap.platform.domain;

/** Stable field-level violation codes shared by REST validation and imports. */
public final class ViolationCodes {

    /** Mandatory field missing or blank. */
    public static final String REQUIRED = "REQUIRED";

    /** Value does not match the required format. */
    public static final String INVALID_FORMAT = "INVALID_FORMAT";

    /** Value exceeds the maximum length. */
    public static final String TOO_LONG = "TOO_LONG";

    /** Value is not one of the allowed values. */
    public static final String INVALID_VALUE = "INVALID_VALUE";

    /** Referenced object (partner, part, relation, site …) does not exist. */
    public static final String UNKNOWN_REFERENCE = "UNKNOWN_REFERENCE";

    /** Unknown catalog code. */
    public static final String UNKNOWN_CODE = "UNKNOWN_CODE";

    /** Business key already used by another object (or twice in an import file). */
    public static final String DUPLICATE_KEY = "DUPLICATE_KEY";

    /** The own tenant must not be used here (e.g. as partner). */
    public static final String SELF_REFERENCE = "SELF_REFERENCE";

    /** Business key in the body differs from the key in the path. */
    public static final String KEY_MISMATCH = "KEY_MISMATCH";

    /** Relation role is not one of the partner's roles. */
    public static final String ROLE_MISMATCH = "ROLE_MISMATCH";

    /** Warning: partner lacks DSP endpoint or DID and cannot be used for exchange yet. */
    public static final String NOT_EXCHANGE_READY = "NOT_EXCHANGE_READY";

    /** Warning: referenced object is inactive. */
    public static final String INACTIVE_REFERENCE = "INACTIVE_REFERENCE";

    private ViolationCodes() {}
}
