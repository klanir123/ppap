package com.msg.ppap.documents.domain;

import com.msg.ppap.platform.domain.PpapException;

/** Business exceptions of the Documents module. */
public final class DocumentErrors {

    public static final String FILE_TOO_LARGE = "FILE_TOO_LARGE";
    public static final String UNSUPPORTED_FILE_TYPE = "UNSUPPORTED_FILE_TYPE";
    public static final String EMPTY_FILE = "EMPTY_FILE";
    public static final String MALWARE_DETECTED = "MALWARE_DETECTED";
    public static final String DOCUMENT_HASH_MISMATCH = "DOCUMENT_HASH_MISMATCH";

    private DocumentErrors() {}

    public static PpapException tooLarge(long maxBytes) {
        return new PpapException(
                PpapException.Kind.TOO_LARGE,
                FILE_TOO_LARGE,
                "File exceeds the maximum size of " + maxBytes + " bytes");
    }

    public static PpapException unsupported(String detected) {
        return new PpapException(
                PpapException.Kind.UNSUPPORTED,
                UNSUPPORTED_FILE_TYPE,
                "File type '" + detected
                        + "' is not allowed (allowed: PDF, PNG, JPEG, WEBP, XLSX, DOCX, CSV, TXT, ZIP)");
    }

    public static PpapException hashMismatch(String expected, String actual) {
        return new PpapException(
                PpapException.Kind.UNPROCESSABLE,
                DOCUMENT_HASH_MISMATCH,
                "Content has SHA-256 " + actual + " but " + expected + " was announced");
    }

    public static PpapException empty() {
        return new PpapException(PpapException.Kind.INVALID, EMPTY_FILE, "File is empty");
    }

    public static PpapException malware(String finding) {
        return new PpapException(
                PpapException.Kind.UNPROCESSABLE, MALWARE_DETECTED, "File rejected by malware scan: " + finding);
    }
}
