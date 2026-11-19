package com.msg.ppap.semantics.api;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;

/** Payload hashes in the format {@code sha256:<lower-case hex>} over canonical bytes. */
public final class PayloadHash {

    /** Prefix of all payload hashes. */
    public static final String PREFIX = "sha256:";

    /** Pattern of a payload hash. */
    public static final Pattern PATTERN = Pattern.compile("^sha256:[0-9a-f]{64}$");

    private PayloadHash() {}

    /** {@code sha256:<hex>} of the given bytes. */
    public static String of(byte[] canonicalBytes) {
        return PREFIX + sha256Hex(canonicalBytes);
    }

    /** Hex SHA-256 of the given bytes. */
    public static String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
