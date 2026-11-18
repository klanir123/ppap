package com.msg.ppap.platform.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Collects field violations while parsing raw (string) input into typed values. Used identically by the REST API
 * and the import so both paths apply exactly the same rules.
 *
 * <p>All methods trim input; blank strings count as absent. Methods return {@code null} for absent or invalid
 * values; call {@link #throwIfInvalid()} before using the results.
 */
public final class Validation {

    private final List<FieldViolation> violations = new ArrayList<>();

    /** Mandatory free text with maximum length. */
    public String required(String field, String raw, int maxLength) {
        String value = trimToNull(raw);
        if (value == null) {
            add(field, ViolationCodes.REQUIRED, "must not be blank");
            return null;
        }
        return checkLength(field, value, maxLength);
    }

    /** Optional free text with maximum length. */
    public String optional(String field, String raw, int maxLength) {
        String value = trimToNull(raw);
        return value == null ? null : checkLength(field, value, maxLength);
    }

    /** Mandatory value that must match a pattern. */
    public String requiredMatching(String field, String raw, int maxLength, Pattern pattern, String expected) {
        String value = required(field, raw, maxLength);
        return value == null ? null : checkPattern(field, value, pattern, expected);
    }

    /** Optional value that must match a pattern if present. */
    public String optionalMatching(String field, String raw, int maxLength, Pattern pattern, String expected) {
        String value = optional(field, raw, maxLength);
        return value == null ? null : checkPattern(field, value, pattern, expected);
    }

    /** Mandatory value parsed by a factory that throws {@link IllegalArgumentException} for invalid input. */
    public <T> T required(String field, String raw, Function<String, T> parser, String expected) {
        String value = trimToNull(raw);
        if (value == null) {
            add(field, ViolationCodes.REQUIRED, "must not be blank");
            return null;
        }
        return parse(field, value, parser, expected);
    }

    /** Optional value parsed by a factory that throws {@link IllegalArgumentException} for invalid input. */
    public <T> T optional(String field, String raw, Function<String, T> parser, String expected) {
        String value = trimToNull(raw);
        return value == null ? null : parse(field, value, parser, expected);
    }

    /** Mandatory enum constant (case-insensitive). */
    public <E extends Enum<E>> E requiredEnum(String field, String raw, Class<E> type) {
        String value = trimToNull(raw);
        if (value == null) {
            add(field, ViolationCodes.REQUIRED, "must not be blank");
            return null;
        }
        return parseEnum(field, value, type);
    }

    /** Optional enum constant (case-insensitive) with default for absent values. */
    public <E extends Enum<E>> E optionalEnum(String field, String raw, Class<E> type, E defaultValue) {
        String value = trimToNull(raw);
        return value == null ? defaultValue : parseEnum(field, value, type);
    }

    /** Non-empty set of enum constants. */
    public <E extends Enum<E>> Set<E> requiredEnumSet(String field, Collection<String> raw, Class<E> type) {
        EnumSet<E> result = EnumSet.noneOf(type);
        if (raw != null) {
            for (String item : raw) {
                String value = trimToNull(item);
                if (value != null) {
                    E parsed = parseEnum(field, value, type);
                    if (parsed != null) {
                        result.add(parsed);
                    }
                }
            }
        }
        if (result.isEmpty() && violations.stream().noneMatch(v -> v.field().equals(field))) {
            add(field, ViolationCodes.REQUIRED, "at least one value required");
        }
        return result;
    }

    /** Records a violation. */
    public Validation add(String field, String code, String message) {
        violations.add(new FieldViolation(field, code, message));
        return this;
    }

    /** Whether violations were recorded. */
    public boolean hasViolations() {
        return !violations.isEmpty();
    }

    /** Recorded violations. */
    public List<FieldViolation> violations() {
        return List.copyOf(violations);
    }

    /** Throws a {@link ValidationException} if violations were recorded. */
    public void throwIfInvalid() {
        if (hasViolations()) {
            throw new ValidationException(violations);
        }
    }

    /** Returns the trimmed value or {@code null} if blank. */
    public static String trimToNull(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String checkLength(String field, String value, int maxLength) {
        if (value.length() > maxLength) {
            add(field, ViolationCodes.TOO_LONG, "must be at most " + maxLength + " characters");
            return null;
        }
        return value;
    }

    private String checkPattern(String field, String value, Pattern pattern, String expected) {
        if (!pattern.matcher(value).matches()) {
            add(field, ViolationCodes.INVALID_FORMAT, "must match " + expected);
            return null;
        }
        return value;
    }

    private <T> T parse(String field, String value, Function<String, T> parser, String expected) {
        try {
            return parser.apply(value);
        } catch (IllegalArgumentException ex) {
            add(field, ViolationCodes.INVALID_FORMAT, "must match " + expected);
            return null;
        }
    }

    private <E extends Enum<E>> E parseEnum(String field, String value, Class<E> type) {
        try {
            return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            add(field, ViolationCodes.INVALID_VALUE, "must be one of " + EnumSet.allOf(type));
            return null;
        }
    }
}
