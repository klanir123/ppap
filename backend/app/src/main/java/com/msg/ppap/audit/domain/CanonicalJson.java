package com.msg.ppap.audit.domain;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * JSON canonicalization following RFC 8785 (JCS) for the value types used in audit events: {@code null}, booleans,
 * strings, integral numbers, maps (keys sorted by UTF-16 code units) and collections. No whitespace.
 *
 * <p>Non-integral numbers are rendered with {@link Double#toString}, which matches ECMAScript only for common values;
 * audit details must therefore not contain floating point numbers. TODO(phase-5): replace with the shared RFC 8785
 * implementation of the {@code semantics} module.
 */
public final class CanonicalJson {

    private CanonicalJson() {}

    /** Canonical JSON text of the value. */
    public static String write(Object value) {
        var out = new StringBuilder();
        append(out, value);
        return out.toString();
    }

    private static void append(StringBuilder out, Object value) {
        switch (value) {
            case null -> out.append("null");
            case Boolean b -> out.append(b);
            case String s -> appendString(out, s);
            case Integer i -> out.append(i);
            case Long l -> out.append(l);
            case Short s -> out.append(s);
            case Byte b -> out.append(b);
            case BigInteger bi -> out.append(bi);
            case BigDecimal bd -> appendDecimal(out, bd);
            case Number n -> appendDouble(out, n.doubleValue());
            case Map<?, ?> map -> appendMap(out, map);
            case Collection<?> collection -> appendArray(out, collection);
            case Object[] array -> appendArray(out, List.of(array));
            default -> appendString(out, value.toString());
        }
    }

    private static void appendDecimal(StringBuilder out, BigDecimal value) {
        BigDecimal stripped = value.stripTrailingZeros();
        if (stripped.scale() <= 0) {
            out.append(stripped.toBigIntegerExact());
        } else {
            appendDouble(out, value.doubleValue());
        }
    }

    private static void appendDouble(StringBuilder out, double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException("NaN and Infinity are not valid JSON numbers");
        }
        if (value == Math.rint(value) && Math.abs(value) < 1e15) {
            out.append((long) value);
        } else {
            out.append(value);
        }
    }

    private static void appendMap(StringBuilder out, Map<?, ?> map) {
        List<String> keys = new ArrayList<>();
        for (Object key : map.keySet()) {
            keys.add(String.valueOf(key));
        }
        keys.sort(String::compareTo); // String.compareTo compares UTF-16 code units, as required by RFC 8785
        out.append('{');
        boolean first = true;
        for (String key : keys) {
            if (!first) {
                out.append(',');
            }
            first = false;
            appendString(out, key);
            out.append(':');
            append(out, map.get(key));
        }
        out.append('}');
    }

    private static void appendArray(StringBuilder out, Collection<?> values) {
        out.append('[');
        boolean first = true;
        for (Object value : values) {
            if (!first) {
                out.append(',');
            }
            first = false;
            append(out, value);
        }
        out.append(']');
    }

    private static void appendString(StringBuilder out, String value) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }
}
