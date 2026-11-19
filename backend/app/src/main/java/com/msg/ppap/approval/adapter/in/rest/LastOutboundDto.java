package com.msg.ppap.approval.adapter.in.rest;

import com.msg.ppap.approval.CaseExchangeStatus.LastOutbound;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** Latest message sent to the partner (delivery badge). */
public record LastOutboundDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "e.g. PPAP_SUBMISSION")
        String messageType,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "PENDING, IN_PROGRESS, DELIVERED or FAILED")
        String status,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant at) {

    static LastOutboundDto of(LastOutbound last) {
        return last == null ? null : new LastOutboundDto(last.messageType(), last.status(), last.at());
    }
}
