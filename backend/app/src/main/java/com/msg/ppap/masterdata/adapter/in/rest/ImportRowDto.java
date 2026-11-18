package com.msg.ppap.masterdata.adapter.in.rest;

import com.msg.ppap.masterdata.application.imports.ImportRow;
import com.msg.ppap.masterdata.application.imports.RowStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;

/** Evaluated import row (preview). */
public record ImportRowDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Row number in the file (header = 1)")
        int rowNumber,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Mapped values by target field")
        Map<String, String> values,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) RowStatus status,

        @Schema(description = "Planned action; absent for rows with errors")
        ImportAction action,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ImportMessageDto> messages) {

    static ImportRowDto from(ImportRow row) {
        return new ImportRowDto(
                row.rowNumber(),
                row.values(),
                row.status(),
                ImportAction.of(row.action()),
                row.messages().stream().map(ImportMessageDto::from).toList());
    }
}
