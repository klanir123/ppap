package com.msg.ppap.platform.domain;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Catena-X Business Partner Number of a site (BPNS), e.g. {@code BPNS00000003AYRE}.
 *
 * @param value the BPNS: prefix {@code BPNS} followed by 12 upper-case alphanumeric characters
 */
public record Bpns(String value) {

    /** Regular expression a valid BPNS has to match. */
    public static final String PATTERN = "^BPNS[0-9A-Z]{12}$";

    private static final Pattern COMPILED = Pattern.compile(PATTERN);

    public Bpns {
        Objects.requireNonNull(value, "BPNS must not be null");
        if (!isValid(value)) {
            throw new IllegalArgumentException("Invalid BPNS: '" + value + "' (expected " + PATTERN + ")");
        }
    }

    /** Returns whether the given string is a syntactically valid BPNS. */
    public static boolean isValid(String candidate) {
        return candidate != null && COMPILED.matcher(candidate).matches();
    }

    @Override
    public String toString() {
        return value;
    }
}
