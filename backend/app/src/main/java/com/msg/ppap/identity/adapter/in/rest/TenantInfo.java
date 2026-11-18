package com.msg.ppap.identity.adapter.in.rest;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Tenant the current user belongs to.
 *
 * @param bpnl business partner number
 * @param name company name
 * @param roleLabel role in the supply chain
 */
public record TenantInfo(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String bpnl,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String roleLabel) {}
