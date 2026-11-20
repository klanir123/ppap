package com.msg.ppap.approval.adapter.in.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** Open and overdue cases of a partner with the health of the dataspace exchange. */
public record PartnerStatsDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String bpnl,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long open,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long overdue,

        @Schema(
                description = "Exchange health of the last 24 h: FAILING (failed/rejected messages), DEGRADED (retries,"
                        + " long pending messages) or OK",
                allowableValues = {"OK", "DEGRADED", "FAILING"})
        String exchangeHealth,

        @Schema(description = "Last message exchanged with the partner")
        Instant lastExchangeAt) {}
