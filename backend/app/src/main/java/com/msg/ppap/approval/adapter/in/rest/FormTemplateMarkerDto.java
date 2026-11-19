package com.msg.ppap.approval.adapter.in.rest;

import io.swagger.v3.oas.annotations.media.Schema;

/** Marker cell identifying a template. */
public record FormTemplateMarkerDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String sheet,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String cell,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String value) {}
