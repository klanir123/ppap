package com.msg.ppap.support;

import java.security.SecureRandom;

/** Random business keys so that integration tests sharing one database do not interfere. */
public final class TestKeys {

    private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final SecureRandom RANDOM = new SecureRandom();

    private TestKeys() {}

    public static String random(int length) {
        var out = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            out.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return out.toString();
    }

    public static String bpnl() {
        return "BPNL" + random(12);
    }

    public static String bpns() {
        return "BPNS" + random(12);
    }

    public static String partNumber() {
        return "P-" + random(8);
    }

    public static String code() {
        return "T-" + random(8);
    }
}
