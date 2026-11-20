package com.msg.ppap.approval.adapter.in.rest;

import com.msg.ppap.approval.domain.DecisionResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Reference of a revision to the decided approval of a sub-supplier (UC-09). */
public record SubSupplierReferenceDto(
        @Schema(description = "Own child case (supplier side only; null on the customer side)")
        UUID childCaseId,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String subSupplierBpnl,
        String subSupplierName,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Exchange id of the sub-supplier case")
        UUID exchangeId,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Case number at the submitting supplier")
        String caseNumber,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Sub-part number of the submitting supplier")
        String partNumber,

        String partName,

        @Schema(description = "Part number at the sub-supplier")
        String supplierPartNumber,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int revision,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) DecisionResult decisionResult,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant decidedAt,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "payloadHash of the signed decision")
        String decisionHash,

        LocalDate conditionDueDate) {}
