package com.msg.ppap.approval.application.port.out;

import com.msg.ppap.approval.domain.FormTemplate;

/** Reads filled Excel forms (XLSX) according to a {@link FormTemplate}. */
public interface FormWorkbookReader {

    /**
     * Opens the workbook.
     *
     * @throws com.msg.ppap.platform.domain.PpapException {@code UNREADABLE_FILE} if it is no readable XLSX
     */
    Workbook open(byte[] content);

    /** An opened workbook; cell access by sheet name and A1 reference. */
    interface Workbook extends AutoCloseable {

        /** Formatted cell value (trimmed), {@code null} if the sheet or cell does not exist or is blank. */
        String cell(String sheet, String reference);

        /** Number of the last row with content in the sheet (1-based), 0 if missing. */
        int lastRow(String sheet);

        @Override
        void close();
    }
}
