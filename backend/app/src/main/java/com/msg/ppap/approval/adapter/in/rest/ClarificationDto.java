package com.msg.ppap.approval.adapter.in.rest;

import com.msg.ppap.approval.domain.Clarification;
import io.swagger.v3.oas.annotations.media.Schema;

/** Open clarification of a received request (UC-04 A1). */
public record ClarificationDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Clarification.Reason reason,

        @Schema(description = "Customer part number that could not be mapped")
        String partnerPartNumber) {

    static ClarificationDto of(Clarification clarification) {
        return clarification == null
                ? null
                : new ClarificationDto(clarification.reason(), clarification.partnerPartNumber());
    }
}
