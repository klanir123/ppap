package com.msg.ppap.masterdata.adapter.in.rest;

import io.swagger.v3.oas.annotations.media.Schema;

/** Target field of an import with its i18n label key. */
public record ImportTargetFieldDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean required,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String labelKey) {}
