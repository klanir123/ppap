package com.msg.ppap.approval.adapter.in.rest;

import io.swagger.v3.oas.annotations.media.Schema;

/** Partner of a case. */
public record CasePartnerDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String bpnl,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name) {}
