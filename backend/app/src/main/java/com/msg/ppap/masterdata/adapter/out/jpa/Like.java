package com.msg.ppap.masterdata.adapter.out.jpa;

import java.util.Locale;

/** Builds case-insensitive {@code LIKE} patterns (escape character {@code \}). */
final class Like {

    private Like() {}

    /** {@code %text%} in lower case with wildcards escaped, or {@code null} for no filter. */
    static String contains(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String escaped = text.strip()
                .toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
