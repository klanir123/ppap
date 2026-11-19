package com.msg.ppap.approval.adapter.in.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;

/** Preview of an Excel form import. */
public record FormImportPreviewDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String templateCode,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Map<String, FormHeaderCellDto> header,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<FormElementDto> elements,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> unknownCodes,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<FormMessageDto> warnings) {}
