package com.msg.ppap.masterdata.application.port.out;

import java.util.List;

/** Reads uploaded import files (CSV or XLSX) into a {@link Table}. */
public interface TabularFileReader {

    /**
     * Reads the file.
     *
     * @param fileName original file name (used to detect the format)
     * @param content file content
     * @param preferredSheets sheet names to prefer in workbooks, case-insensitive (otherwise the first sheet)
     * @param maxRows maximum number of data rows
     * @throws com.msg.ppap.platform.domain.PpapException with code {@code UNSUPPORTED_FILE_TYPE},
     *     {@code EMPTY_FILE}, {@code TOO_MANY_ROWS} or {@code UNREADABLE_FILE}
     */
    Table read(String fileName, byte[] content, List<String> preferredSheets, int maxRows);
}
