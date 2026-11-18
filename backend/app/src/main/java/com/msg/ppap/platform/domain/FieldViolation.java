package com.msg.ppap.platform.domain;

import java.util.Objects;

/**
 * A rule violation of a single input field.
 *
 * @param field field name as used in the API (e.g. {@code bpnl}, {@code requirements.VDA-01})
 * @param code stable machine-readable code (see {@link ViolationCodes})
 * @param message English description; the UI translates via {@code code}
 */
public record FieldViolation(String field, String code, String message) {

    public FieldViolation {
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
    }
}
