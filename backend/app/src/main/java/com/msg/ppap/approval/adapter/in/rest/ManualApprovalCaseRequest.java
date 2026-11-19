package com.msg.ppap.approval.adapter.in.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Capture a request received outside the dataspace (supplier). */
public record ManualApprovalCaseRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String partnerBpnl,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID partId,

        @Schema(description = "Customer part number; from the part mapping if omitted")
        String partnerPartNumber,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String reasonCode,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String levelCode,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDate dueDate,
        String remarks,

        @Schema(description = "Elements; default: elements REQUIRED at the level")
        List<RequestedElementInput> elements,

        @Schema(description = "Reference of the customer, e.g. e-mail or portal id")
        String externalReference) {}
