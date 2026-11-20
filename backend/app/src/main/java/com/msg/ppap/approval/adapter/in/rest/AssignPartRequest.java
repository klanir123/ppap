package com.msg.ppap.approval.adapter.in.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** Assigns the own part to a received request that needs clarification (UC-04 A1). */
public record AssignPartRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID partId,

        @Schema(description = "Also create the part mapping for future requests (default true)")
        Boolean saveMapping,

        @Schema(
                description = "Replace an existing mapping of this part in the relation with the customer part number"
                        + " of the request (default false; otherwise an existing mapping fails with DUPLICATE_KEY)")
        Boolean replaceExistingMapping) {}
