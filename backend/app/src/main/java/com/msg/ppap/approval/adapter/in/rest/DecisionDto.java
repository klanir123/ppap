package com.msg.ppap.approval.adapter.in.rest;

import com.msg.ppap.approval.domain.DecisionResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Decision on a revision (immutable, signed). */
public record DecisionDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int revision,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) DecisionResult result,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ConditionDto> conditions,
        LocalDate conditionDueDate,
        String comment,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<FindingDto> elementFindings,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant decidedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String decidedBy,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String payloadHash,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) SignatureDto signature) {}
