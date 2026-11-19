package com.msg.ppap.documents.adapter.in.rest;

import java.nio.charset.StandardCharsets;

/**
 * Builds {@code Content-Disposition} values per RFC 6266 with an ASCII fallback {@code filename} and the exact name
 * as RFC 5987/8187 {@code filename*}.
 */
final class ContentDisposition6266 {

    private ContentDisposition6266() {}

    static String of(String type, String fileName) {
        return type + "; filename=\"" + asciiFallback(fileName) + "\"; filename*=UTF-8''" + encode(fileName);
    }

    static String asciiFallback(String fileName) {
        var out = new StringBuilder(fileName.length());
        for (char c : fileName.toCharArray()) {
            if (c < 0x20 || c > 0x7e || c == '"' || c == '\\' || c == '%' || c == ';') {
                out.append('_');
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    /** RFC 5987 {@code value-chars}: attr-char or percent-encoded UTF-8 octets. */
    static String encode(String value) {
        var out = new StringBuilder();
        for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
            int c = b & 0xff;
            boolean attrChar = (c >= 'a' && c <= 'z')
                    || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9')
                    || "!#$&+-.^_`|~".indexOf(c) >= 0;
            if (attrChar) {
                out.append((char) c);
            } else {
                out.append('%').append(String.format("%02X", c));
            }
        }
        return out.toString();
    }
}
