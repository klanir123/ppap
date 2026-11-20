package com.msg.ppap.approval.adapter.in.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Completeness of a revision. */
public record CompletenessDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean complete,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int requiredTotal,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int requiredDone,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<CompletenessIssueDto> issues,

        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Non-blocking hints of the whole revision, e.g. SUB_SUPPLIER_PENDING (UC-09); elementCode"
                        + " is null")
        List<CompletenessIssueDto> hints) {}
