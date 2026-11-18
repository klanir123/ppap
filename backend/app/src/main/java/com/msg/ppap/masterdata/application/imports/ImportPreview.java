package com.msg.ppap.masterdata.application.imports;

import java.util.List;
import java.util.Map;

/**
 * Result of an import preview (nothing is written).
 *
 * @param entity imported entity
 * @param fileName uploaded file name
 * @param sheet used sheet (XLSX) or {@code null}
 * @param delimiter detected delimiter (CSV) or {@code null}
 * @param columns headers of the file
 * @param suggestedMapping column to field (or {@code null}) as used for validation
 * @param rows evaluated rows (at most the configured preview size)
 * @param summary counts over all rows
 */
public record ImportPreview(
        ImportEntity entity,
        String fileName,
        String sheet,
        String delimiter,
        List<String> columns,
        Map<String, String> suggestedMapping,
        List<ImportRow> rows,
        ImportSummary summary) {}
