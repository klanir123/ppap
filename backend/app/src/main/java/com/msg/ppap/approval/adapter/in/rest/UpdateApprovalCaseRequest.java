package com.msg.ppap.approval.adapter.in.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/** Change the header of a draft. */
public record UpdateApprovalCaseRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String reasonCode,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String levelCode,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDate dueDate,
        String remarks) {}
