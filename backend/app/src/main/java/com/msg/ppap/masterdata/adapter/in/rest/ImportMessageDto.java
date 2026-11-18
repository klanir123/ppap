package com.msg.ppap.masterdata.adapter.in.rest;

import com.msg.ppap.masterdata.application.imports.ImportMessage;
import io.swagger.v3.oas.annotations.media.Schema;

/** Validation message of an import row. */
public record ImportMessageDto(
        String field,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String message) {

    static ImportMessageDto from(ImportMessage message) {
        return new ImportMessageDto(message.field(), message.code(), message.message());
    }
}
