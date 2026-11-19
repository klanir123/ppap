package com.msg.ppap.documents.domain;

import java.util.Locale;
import java.util.Set;

/**
 * Whitelist of document media types (contract: PDF, PNG, JPEG, WEBP, XLSX, DOCX, CSV, TXT, ZIP). Checked against the
 * type detected from the content, never against the client's {@code Content-Type} or the file extension alone.
 */
public final class AllowedMediaTypes {

    public static final String PDF = "application/pdf";
    public static final String PNG = "image/png";
    public static final String JPEG = "image/jpeg";
    public static final String WEBP = "image/webp";
    public static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    public static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    public static final String CSV = "text/csv";
    public static final String TXT = "text/plain";
    public static final String ZIP = "application/zip";

    /** All allowed media types. */
    public static final Set<String> ALL = Set.of(PDF, PNG, JPEG, WEBP, XLSX, DOCX, CSV, TXT, ZIP);

    private AllowedMediaTypes() {}

    /** Whether the (detected) media type is allowed; parameters such as {@code charset} are ignored. */
    public static boolean isAllowed(String mediaType) {
        return mediaType != null && ALL.contains(baseType(mediaType));
    }

    /** Media type without parameters, lower case. */
    public static String baseType(String mediaType) {
        int semicolon = mediaType.indexOf(';');
        String base = semicolon < 0 ? mediaType : mediaType.substring(0, semicolon);
        return base.strip().toLowerCase(Locale.ROOT);
    }
}
