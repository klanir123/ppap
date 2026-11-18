package com.msg.ppap.masterdata.adapter.in.rest;

import com.msg.ppap.masterdata.application.imports.ImportResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Result of an import (also returned with status 422 when nothing was imported). */
public record ImportResultResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String entity,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int created,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int updated,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int unchanged,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int failed,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ImportFailureDto> failures) {

    static ImportResultResponse from(ImportResult result) {
        return new ImportResultResponse(
                result.entity(),
                result.created(),
                result.updated(),
                result.unchanged(),
                result.failed(),
                result.failures().stream().map(ImportFailureDto::from).toList());
    }
}
