package com.msg.ppap.masterdata.adapter.in.rest;

import com.msg.ppap.masterdata.application.CatalogElementCommand;
import com.msg.ppap.masterdata.domain.CatalogElement;
import com.msg.ppap.masterdata.domain.EvidenceType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

/**
 * Create or replace a catalog element; also the element format of the catalog export/import.
 *
 * @param active {@code null} keeps the current state (new elements are active)
 */
public record CatalogElementRequest(
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

        Integer sortOrder,
        Boolean active) {

    CatalogElementCommand toCommand() {
        return new CatalogElementCommand(
                code,
                nameDe,
                nameEn,
                descriptionDe,
                descriptionEn,
                referenceVda,
                referenceAiag,
                evidenceType == null ? null : evidenceType.name(),
                valueSchema,
                sortOrder,
                active);
    }

    static CatalogElementRequest from(CatalogElement element) {
        var v = element.values();
        return new CatalogElementRequest(
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
                element.active());
    }
}
