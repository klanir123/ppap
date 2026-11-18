package com.msg.ppap.identity.domain;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Catena-X Business Partner Number of a legal entity (BPNL), e.g. {@code BPNL00000003AYRE}.
 *
 * @param value the BPNL: prefix {@code BPNL} followed by 12 upper-case alphanumeric characters
 */
public record Bpnl(String value) {

    /** Regular expression a valid BPNL has to match. */
    public static final String PATTERN = "^BPNL[0-9A-Z]{12}$";

    private static final Pattern COMPILED = Pattern.compile(PATTERN);

    public Bpnl {
        Objects.requireNonNull(value, "BPNL must not be null");
        if (!isValid(value)) {
            throw new IllegalArgumentException("Invalid BPNL: '" + value + "' (expected " + PATTERN + ")");
        }
    }

    /** Returns whether the given string is a syntactically valid BPNL. */
    public static boolean isValid(String candidate) {
        return candidate != null && COMPILED.matcher(candidate).matches();
    }

    @Override
    public String toString() {
        return value;
    }
}
