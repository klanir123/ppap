package com.msg.ppap.approval.adapter.in.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** Assigns the own part to a received request that needs clarification (UC-04 A1). */
public record AssignPartRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID partId,

        @Schema(description = "Also create the part mapping for future requests (default true)")
        Boolean saveMapping) {}
