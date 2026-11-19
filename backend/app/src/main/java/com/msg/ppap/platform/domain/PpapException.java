package com.msg.ppap.platform.domain;

import java.util.LinkedHashMap;
import java.util.Map;
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
        UNSUPPORTED,
        FORBIDDEN,
        UNAUTHORIZED
    }

    private final String code;
    private final Kind kind;
    private final Map<String, Object> properties = new LinkedHashMap<>();

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

    /** Additional machine-readable problem properties (e.g. {@code currentStatus}); JSON-serializable values. */
    public Map<String, Object> properties() {
        return Map.copyOf(properties);
    }

    /** Adds a problem property (ignored for {@code null} values). */
    protected PpapException withProperty(String name, Object value) {
        if (value != null) {
            properties.put(name, value);
        }
        return this;
    }
}
