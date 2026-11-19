package com.msg.ppap.approval.adapter.in.rest;

import com.msg.ppap.approval.domain.DecisionResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

/** Decision with explicit confirmation. */
public record DecisionRequest(
        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                description = "APPROVED, CONDITIONALLY_APPROVED or REJECTED")
        DecisionResult result,

        List<ConditionDto> conditions,
        LocalDate conditionDueDate,
        String comment,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Boolean confirmation) {}
