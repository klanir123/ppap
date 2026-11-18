package com.msg.ppap.masterdata.adapter.in.rest;

import com.msg.ppap.masterdata.application.imports.ImportFailure;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Row that could not be imported. */
public record ImportFailureDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int rowNumber,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ImportMessageDto> messages) {

    static ImportFailureDto from(ImportFailure failure) {
        return new ImportFailureDto(
                failure.rowNumber(),
                failure.messages().stream().map(ImportMessageDto::from).toList());
    }
}
