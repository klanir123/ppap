package com.msg.ppap.platform.domain;

import java.util.Objects;

/**
 * Base class of expected business exceptions. Carries a stable {@link #code()} and a protocol-neutral
 * {@link Kind} that the web layer maps to an HTTP status.
 */
public class PpapException extends RuntimeException {

    /** Category of the failure. */
    public enum Kind {
        INVALID,
        NOT_FOUND,
        CONFLICT,
        PRECONDITION_FAILED,
        UNPROCESSABLE,
        TOO_LARGE,
        UNSUPPORTED
    }

    private final String code;
    private final Kind kind;

    public PpapException(Kind kind, String code, String message) {
        super(message);
        this.kind = Objects.requireNonNull(kind, "kind");
        this.code = Objects.requireNonNull(code, "code");
    }

    /** Stable machine-readable error code. */
    public String code() {
        return code;
    }

    /** Failure category. */
    public Kind kind() {
        return kind;
    }
}
