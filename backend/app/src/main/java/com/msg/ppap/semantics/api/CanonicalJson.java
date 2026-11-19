package com.msg.ppap.semantics.api;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;
import org.erdtman.jcs.JsonCanonicalizer;
import tools.jackson.databind.json.JsonMapper;

/**
 * JSON Canonicalization Scheme (RFC 8785) – the single canonicalization implementation of the application, used for
 * exchange payload hashes and signatures as well as for the audit hash chain.
 *
 * <p>Values are first serialized with Jackson (maps, collections, records, strings, numbers, booleans, {@code null};
 * {@code java.time} values as ISO-8601 strings) and then canonicalized with the reference implementation
 * {@code io.github.erdtman:java-json-canonicalization}: object members sorted by UTF-16 code units, no whitespace,
 * numbers serialized like ECMAScript ({@code 1.0 → 1}, IEEE-754 double precision, see RFC 8785 §3.2.2.3).
 */
public final class CanonicalJson {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private CanonicalJson() {}

    /** Canonical UTF-8 bytes of a Java value tree. */
    public static byte[] canonicalize(Object value) {
        return write(value).getBytes(StandardCharsets.UTF_8);
    }

    /** Canonical JSON text of a Java value tree. */
    public static String write(Object value) {
        checkFinite(value);
        return canonicalizeJson(MAPPER.writeValueAsString(value));
    }

    /**
     * Canonicalizes JSON text.
     *
     * @throws IllegalArgumentException if the text is not valid JSON
     */
    public static String canonicalizeJson(String json) {
        try {
            String trimmed = json.strip();
            if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
                // the reference implementation only accepts objects and arrays at top level
                String wrapped = new JsonCanonicalizer("[" + trimmed + "]").getEncodedString();
                return wrapped.substring(1, wrapped.length() - 1);
            }
            return new JsonCanonicalizer(json).getEncodedString();
        } catch (IOException | RuntimeException ex) {
            throw new IllegalArgumentException("Not canonicalizable JSON: " + ex.getMessage(), ex);
        }
    }

    /** Canonicalizes UTF-8 encoded JSON. */
    public static byte[] canonicalizeJson(byte[] json) {
        return canonicalizeJson(new String(json, StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8);
    }

    private static void checkFinite(Object value) {
        switch (value) {
            case Double d
            when d.isNaN() || d.isInfinite() ->
                throw new IllegalArgumentException("NaN and Infinity are not valid JSON numbers");
            case Float f
            when f.isNaN() || f.isInfinite() ->
                throw new IllegalArgumentException("NaN and Infinity are not valid JSON numbers");
            case Map<?, ?> map -> map.values().forEach(CanonicalJson::checkFinite);
            case Collection<?> collection -> collection.forEach(CanonicalJson::checkFinite);
            case Object[] array -> {
                for (Object item : array) {
                    checkFinite(item);
                }
            }
            case null, default -> {}
        }
    }
}
