package com.msg.ppap.approval.adapter.in.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** Parent case of a sub-supplier request (UC-09). */
public record ParentCaseDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String caseNumber,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Customer of the parent case")
        CasePartnerDto partner) {}
