package com.msg.ppap.approval.adapter.in.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/** Entry of the activity feed (UC-11). */
public record ActivityDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant at,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID caseId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String caseNumber,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Case action, e.g. SEND")
        String action,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String actor,

        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                description = "User name, or the partner name instead of partner:<BPNL>")
        String actorDisplay,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) CasePartnerDto partner,
        String fromStatus,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String toStatus) {}
