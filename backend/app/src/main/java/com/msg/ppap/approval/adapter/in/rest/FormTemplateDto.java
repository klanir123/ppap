package com.msg.ppap.approval.adapter.in.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;

/** Excel form template. */
public record FormTemplateDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String nameDe,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String nameEn,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) FormTemplateMarkerDto marker,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<FormTemplateHeaderFieldDto> header,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) FormTemplateElementTableDto elementTable,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Form text → OK | NOK | NA")
        Map<String, String> selfAssessmentValues,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean active) {}
