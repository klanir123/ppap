package com.msg.ppap.approval.adapter.in.rest;

import com.msg.ppap.approval.domain.CaseStatus;
import com.msg.ppap.approval.domain.DecisionResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Decided sub-supplier request that can be referenced in the own submission (UC-09). */
public record SubSupplierCandidateDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID caseId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String caseNumber,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID exchangeId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) CasePartnerDto subSupplier,
        String partNumber,
        String partName,
        String supplierPartNumber,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) CaseStatus status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int revision,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) DecisionResult decisionResult,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant decidedAt,
        String decisionHash,
        LocalDate conditionDueDate,

        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Only approvals (incl. conditional) can be referenced; rejections are informative")
        boolean referenceable) {}
