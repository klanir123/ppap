package com.msg.ppap.masterdata.adapter.out.file;

import org.apache.poi.openxml4j.util.ZipSecureFile;

/** Global Apache POI limits against zip bombs and oversized workbooks. */
final class PoiSecurity {

    /** Minimum compression ratio (uncompressed may be at most 100× the compressed size). */
    static final double MIN_INFLATE_RATIO = 0.01;

    /** Maximum uncompressed size of a single zip entry (sheet XML). */
    static final long MAX_ENTRY_SIZE = 100L * 1024 * 1024;

    /** Maximum size of extracted text. */
    static final long MAX_TEXT_SIZE = 50L * 1024 * 1024;

    private PoiSecurity() {}

    static void apply() {
        ZipSecureFile.setMinInflateRatio(MIN_INFLATE_RATIO);
        ZipSecureFile.setMaxEntrySize(MAX_ENTRY_SIZE);
        ZipSecureFile.setMaxTextSize(MAX_TEXT_SIZE);
    }
}
