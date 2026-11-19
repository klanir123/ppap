package com.msg.ppap.approval.adapter.in.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Create a draft request (customer). */
public record CreateApprovalCaseRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID partId,

        @Schema(description = "Supplier relation; derived from the part mapping if omitted")
        UUID relationId,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String reasonCode,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String levelCode,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDate dueDate,
        String remarks,

        @Schema(description = "Elements; default: elements REQUIRED at the level")
        List<RequestedElementInput> elements) {}
