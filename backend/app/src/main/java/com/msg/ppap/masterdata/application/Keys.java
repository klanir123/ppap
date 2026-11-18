package com.msg.ppap.masterdata.application;

import com.msg.ppap.platform.domain.Bpnl;
import com.msg.ppap.platform.domain.Bpns;
import com.msg.ppap.platform.domain.Validation;
import com.msg.ppap.platform.domain.ValidationException;
import com.msg.ppap.platform.domain.ViolationCodes;
import java.util.Locale;

/** Parsing helpers for keys used in paths and filters. */
final class Keys {

    private Keys() {}

    /** Parses an optional BPNL filter parameter. */
    static Bpnl optionalBpnl(String field, String raw) {
        String value = Validation.trimToNull(raw);
        if (value == null) {
            return null;
        }
        if (!Bpnl.isValid(value)) {
            throw ValidationException.of(field, ViolationCodes.INVALID_FORMAT, "must match " + Bpnl.PATTERN);
        }
        return new Bpnl(value);
    }

    /** Parses an optional BPNS filter parameter. */
    static Bpns optionalBpns(String field, String raw) {
        String value = Validation.trimToNull(raw);
        if (value == null) {
            return null;
        }
        if (!Bpns.isValid(value)) {
            throw ValidationException.of(field, ViolationCodes.INVALID_FORMAT, "must match " + Bpns.PATTERN);
        }
        return new Bpns(value);
    }

    /** Parses an optional enum filter parameter. */
    static <E extends Enum<E>> E optionalEnum(String field, String raw, Class<E> type) {
        String value = Validation.trimToNull(raw);
        if (value == null) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw ValidationException.of(field, ViolationCodes.INVALID_VALUE, "invalid value");
        }
    }

    /** Rejects a body key that differs from the path key. */
    static void checkKeyMatches(String field, String pathKey, String bodyKey) {
        String body = Validation.trimToNull(bodyKey);
        if (body != null && !body.equals(pathKey)) {
            throw ValidationException.of(field, ViolationCodes.KEY_MISMATCH, "must match the key in the path");
        }
    }

    /** Normalizes a full-text filter. */
    static String text(String raw) {
        return Validation.trimToNull(raw);
    }
}
