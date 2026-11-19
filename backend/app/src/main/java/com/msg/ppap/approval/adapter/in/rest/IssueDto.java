package com.msg.ppap.approval.adapter.in.rest;

import io.swagger.v3.oas.annotations.media.Schema;

/** Completeness issue of an element. */
public record IssueDto(
        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                description =
                        "MISSING_SELF_ASSESSMENT, COMMENT_REQUIRED, DOCUMENT_REQUIRED, VALUES_REQUIRED, VALUE_SCHEMA_VIOLATION")
        String code,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String message,

        @Schema(description = "JSON pointer into the values (VALUE_SCHEMA_VIOLATION)")
        String field) {}
