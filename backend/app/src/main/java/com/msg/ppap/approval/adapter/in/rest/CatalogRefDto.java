package com.msg.ppap.approval.adapter.in.rest;

import io.swagger.v3.oas.annotations.media.Schema;

/** Snapshot of a catalogue entry (reason, level). */
public record CatalogRefDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String nameDe,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String nameEn) {}
