package com.msg.ppap.approval.application;

import com.msg.ppap.approval.domain.SelfAssessment;
import java.util.List;
import java.util.Map;

/**
 * Result of reading a filled Excel form against the draft revision.
 *
 * @param templateCode recognized (or given) template
 * @param header header field → value and cell
 * @param elements rows of requested elements
 * @param unknownCodes element codes of the form that are not requested
 * @param warnings general findings (e.g. part number mismatch)
 */
public record ExcelPreview(
        String templateCode,
        Map<String, HeaderCell> header,
        List<ElementRow> elements,
        List<String> unknownCodes,
        List<Message> warnings) {

    /** Header value with its cell ({@code Sheet!C4}). */
    public record HeaderCell(String value, String cell) {}

    /** An element row of the form. */
    public record ElementRow(
            String code,
            SelfAssessment selfAssessment,
            String comment,
            Object values,
            String cell,
            RowStatus status,
            List<Message> messages) {}

    /** Finding with stable code. */
    public record Message(String code, String message) {}

    /** Status of a row. */
    public enum RowStatus {
        OK,
        WARNING,
        ERROR
    }

    /**
     * Rows to apply.
     *
     * @param elementCodes codes to take over; empty = all rows without errors
     * @param overwrite overwrite values already entered in the draft
     */
    public record Selection(List<String> elementCodes, boolean overwrite) {

        public Selection {
            elementCodes = elementCodes == null ? List.of() : List.copyOf(elementCodes);
        }
    }
}
