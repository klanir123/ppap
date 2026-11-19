package com.msg.ppap.approval.adapter.in.rest;

import io.swagger.v3.oas.annotations.media.Schema;

/** Open and overdue cases of a partner. */
public record PartnerStatsDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String bpnl,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long open,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long overdue) {}
