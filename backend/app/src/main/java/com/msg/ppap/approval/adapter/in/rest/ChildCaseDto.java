package com.msg.ppap.approval.adapter.in.rest;

import com.msg.ppap.approval.domain.CaseStatus;
import com.msg.ppap.approval.domain.DecisionResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** Sub-supplier request (child case) of a case (UC-09). */
public record ChildCaseDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String caseNumber,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Sub-supplier")
        CasePartnerDto partner,

        @Schema(description = "Own sub-part number") String partNumber,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) CaseStatus status,
        DecisionResult lastDecisionResult) {}
