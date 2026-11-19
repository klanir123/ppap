package com.msg.ppap.approval.adapter.in.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/** Request of the customer (embedded in the case). */
public record CaseRequestDto(
        @Schema(description = "Time of sending, null while DRAFT")
        Instant requestedAt,

        String requestedBy,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<RequestedElementDto> elements,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<DocumentRefDto> attachments) {}
