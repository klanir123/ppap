package com.msg.ppap.masterdata.application.imports;

import com.msg.ppap.masterdata.application.ChangeAction;
import java.util.List;
import java.util.Map;

/**
 * Evaluated import row.
 *
 * @param rowNumber row number in the file (header = 1)
 * @param values mapped values by target field
 * @param cells raw cells in column order (for the error report)
 * @param status validation status
 * @param action planned action ({@code null} for erroneous rows)
 * @param messages errors and warnings
 */
public record ImportRow(
        int rowNumber,
        Map<String, String> values,
        List<String> cells,
        RowStatus status,
        ChangeAction action,
        List<ImportMessage> messages) {}
