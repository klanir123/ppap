package com.msg.ppap.approval.adapter.in.rest;

import com.msg.ppap.approval.domain.EvidenceType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

/** Element definition frozen at request time. */
public record ElementSnapshotDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String nameDe,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String nameEn,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) EvidenceType evidenceType,

        @Schema(description = "JSON Schema (draft 2020-12) of the structured values")
        Map<String, Object> valueSchema) {}
