package com.msg.ppap.identity.adapter.in.rest;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Public information about the participant operating this connector.
 *
 * @param bpnl business partner number
 * @param name company name
 * @param roleLabel role in the supply chain (e.g. OEM, Tier 1)
 */
public record ParticipantInfo(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String bpnl,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String roleLabel) {}
