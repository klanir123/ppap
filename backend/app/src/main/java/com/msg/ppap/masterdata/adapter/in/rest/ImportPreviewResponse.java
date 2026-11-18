package com.msg.ppap.masterdata.adapter.in.rest;

import com.msg.ppap.masterdata.application.imports.ImportPreview;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;

/** Import preview: column mapping and row-level validation report. Nothing has been written. */
public record ImportPreviewResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String entity,
        String fileName,
        @Schema(description = "Used sheet (XLSX)") String sheet,
        @Schema(description = "Detected delimiter (CSV)") String delimiter,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> columns,

        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Column to target field (null = not imported) as used for validation")
        Map<String, String> suggestedMapping,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ImportTargetFieldDto> targetFields,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "At most 500 rows")
        List<ImportRowDto> rows,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ImportSummaryDto summary) {

    static ImportPreviewResponse from(ImportPreview preview) {
        var entity = preview.entity();
        return new ImportPreviewResponse(
                entity.pathValue(),
                preview.fileName(),
                preview.sheet(),
                preview.delimiter(),
                preview.columns(),
                preview.suggestedMapping(),
                entity.fields().stream()
                        .map(f -> new ImportTargetFieldDto(f.name(), f.required(), entity.labelKey(f.name())))
                        .toList(),
                preview.rows().stream().map(ImportRowDto::from).toList(),
                ImportSummaryDto.from(preview.summary()));
    }
}
