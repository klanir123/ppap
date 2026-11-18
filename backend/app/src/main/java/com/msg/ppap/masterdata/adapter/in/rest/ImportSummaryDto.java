package com.msg.ppap.masterdata.adapter.in.rest;

import com.msg.ppap.masterdata.application.imports.ImportSummary;
import io.swagger.v3.oas.annotations.media.Schema;

/** Counts over all rows of an import file. */
public record ImportSummaryDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int total,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int ok,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int warnings,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int errors,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int create,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int update,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int unchanged) {

    static ImportSummaryDto from(ImportSummary s) {
        return new ImportSummaryDto(s.total(), s.ok(), s.warnings(), s.errors(), s.create(), s.update(), s.unchanged());
    }
}
