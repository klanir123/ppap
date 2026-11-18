package com.msg.ppap.masterdata.domain;

import static com.msg.ppap.masterdata.domain.MasterdataRules.NAME_MAX;
import static com.msg.ppap.masterdata.domain.MasterdataRules.TEXT_MAX;

import com.msg.ppap.platform.domain.Validation;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Validated attributes of a {@link CatalogElement}.
 *
 * @param code stable element code (business key)
 * @param nameDe German name
 * @param nameEn English name
 * @param descriptionDe German description
 * @param descriptionEn English description
 * @param referenceVda reference to VDA volume 2
 * @param referenceAiag reference to AIAG PPAP
 * @param evidenceType expected evidence
 * @param valueSchema JSON Schema (draft 2020-12) for structured values as JSON object tree, or {@code null}; its
 *     validity is checked by the application layer
 * @param sortOrder display order
 */
public record CatalogElementValues(
        CatalogCode code,
        String nameDe,
        String nameEn,
        String descriptionDe,
        String descriptionEn,
        String referenceVda,
        String referenceAiag,
        EvidenceType evidenceType,
        Map<String, Object> valueSchema,
        int sortOrder) {

    public CatalogElementValues {
        valueSchema = valueSchema == null || valueSchema.isEmpty()
                ? null
                : Collections.unmodifiableMap(new LinkedHashMap<>(valueSchema));
    }

    /** Parses and validates raw input. */
    public static CatalogElementValues parse(
            String code,
            String nameDe,
            String nameEn,
            String descriptionDe,
            String descriptionEn,
            String referenceVda,
            String referenceAiag,
            String evidenceType,
            Map<String, Object> valueSchema,
            Integer sortOrder) {
        var v = new Validation();
        var values = new CatalogElementValues(
                v.required("code", code, CatalogCode::new, MasterdataRules.CATALOG_CODE.pattern()),
                v.required("nameDe", nameDe, NAME_MAX),
                v.required("nameEn", nameEn, NAME_MAX),
                v.optional("descriptionDe", descriptionDe, TEXT_MAX),
                v.optional("descriptionEn", descriptionEn, TEXT_MAX),
                v.optional("referenceVda", referenceVda, NAME_MAX),
                v.optional("referenceAiag", referenceAiag, NAME_MAX),
                v.requiredEnum("evidenceType", evidenceType, EvidenceType.class),
                valueSchema,
                sortOrder == null ? 0 : sortOrder);
        v.throwIfInvalid();
        return values;
    }
}
