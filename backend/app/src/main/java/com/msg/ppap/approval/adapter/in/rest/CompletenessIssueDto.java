package com.msg.ppap.approval.adapter.in.rest;

import io.swagger.v3.oas.annotations.media.Schema;

/** Completeness issue with element code. */
public record CompletenessIssueDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String elementCode,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String message,

        @Schema(description = "JSON pointer into the values (VALUE_SCHEMA_VIOLATION)")
        String field) {}
