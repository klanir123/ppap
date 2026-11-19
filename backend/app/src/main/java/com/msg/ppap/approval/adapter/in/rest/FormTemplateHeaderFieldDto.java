package com.msg.ppap.approval.adapter.in.rest;

import io.swagger.v3.oas.annotations.media.Schema;

/** Header field of a template. */
public record FormTemplateHeaderFieldDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String field,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String sheet,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String cell) {}
