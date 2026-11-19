package com.msg.ppap.approval.adapter.in.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;

/** Dashboard figures. */
public record CaseStatsDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long open,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long overdue,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long inReview,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long approvedLast30Days,

        @Schema(description = "Average days from request to approval")
        Double averageLeadTimeDays,

        @Schema(description = "Share of approvals among decisions (0..1)")
        Double approvalRate,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Map<String, Long> byStatus,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<PartnerStatsDto> byPartner,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Received requests to clarify (UC-04 A1)")
        long needsClarification) {}
