package com.msg.ppap.approval.adapter.in.rest;

import com.msg.ppap.approval.domain.CaseAction;
import com.msg.ppap.approval.domain.CaseStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** Status change of a case. */
public record TimelineEntryDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant at,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String actor,

        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Display name of the actor: partner name instead of partner:<BPNL>")
        String actorDisplay,

        CaseStatus from,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) CaseStatus to,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) CaseAction action,
        String comment) {}
