package com.msg.ppap.documents.domain;

import java.text.Normalizer;

/** Sanitizes client-supplied file names: no paths, no control characters, bounded length. */
public final class FileNames {

    /** Maximum length of a stored file name. */
    public static final int MAX_LENGTH = 255;

    private FileNames() {}

    /** Sanitized file name; {@code fallback} if nothing usable remains. */
    public static String sanitize(String raw, String fallback) {
        if (raw == null) {
            return fallback;
        }
        String name = Normalizer.normalize(raw, Normalizer.Form.NFC);
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        name = name.substring(slash + 1);
        var out = new StringBuilder(name.length());
        name.codePoints()
                .filter(cp -> !Character.isISOControl(cp) && cp != '"' && Character.getType(cp) != Character.FORMAT)
                .forEach(out::appendCodePoint);
        String cleaned = out.toString().strip();
        while (cleaned.startsWith(".")) {
            cleaned = cleaned.substring(1);
        }
        if (cleaned.isBlank()) {
            return fallback;
        }
        if (cleaned.length() > MAX_LENGTH) {
            int dot = cleaned.lastIndexOf('.');
            String extension = dot > 0 && cleaned.length() - dot <= 16 ? cleaned.substring(dot) : "";
            cleaned = cleaned.substring(0, MAX_LENGTH - extension.length()) + extension;
        }
        return cleaned;
    }
}
