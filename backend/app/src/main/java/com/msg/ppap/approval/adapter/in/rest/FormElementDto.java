package com.msg.ppap.approval.adapter.in.rest;

import com.msg.ppap.approval.application.ExcelPreview;
import com.msg.ppap.approval.domain.SelfAssessment;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Element row of a form. */
public record FormElementDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code,
        SelfAssessment selfAssessment,
        String comment,

        @Schema(description = "Structured values (not read by the current templates)")
        Object values,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String cell,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ExcelPreview.RowStatus status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<FormMessageDto> messages) {}
