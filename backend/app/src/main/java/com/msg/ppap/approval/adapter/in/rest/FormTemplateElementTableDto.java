package com.msg.ppap.approval.adapter.in.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

/** Element table of a template. */
public record FormTemplateElementTableDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String sheet,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int firstRow,
        Integer lastRow,

        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                description = "code, selfAssessment, comment → column letter")
        Map<String, String> columns) {}
