package com.msg.ppap.masterdata.adapter.in.rest;

import com.msg.ppap.masterdata.domain.CatalogElement;
import com.msg.ppap.masterdata.domain.EvidenceType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Submission element of the catalog. */
public record CatalogElementDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String nameDe,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String nameEn,
        String descriptionDe,
        String descriptionEn,
        String referenceVda,
        String referenceAiag,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) EvidenceType evidenceType,

        @Schema(description = "JSON Schema (draft 2020-12) for structured values")
        Map<String, Object> valueSchema,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int sortOrder,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean active,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long version,
        Instant createdAt,
        Instant updatedAt) {

    static CatalogElementDto from(CatalogElement element) {
        var v = element.values();
        var m = element.metadata();
        return new CatalogElementDto(
                element.id(),
                v.code().value(),
                v.nameDe(),
                v.nameEn(),
                v.descriptionDe(),
                v.descriptionEn(),
                v.referenceVda(),
                v.referenceAiag(),
                v.evidenceType(),
                v.valueSchema(),
                v.sortOrder(),
                element.active(),
                m.version(),
                m.createdAt(),
                m.updatedAt());
    }
}
