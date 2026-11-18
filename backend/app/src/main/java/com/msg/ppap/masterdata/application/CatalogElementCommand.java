package com.msg.ppap.masterdata.application;

import com.msg.ppap.masterdata.domain.CatalogElementValues;
import java.util.Map;

/**
 * Raw input for creating or updating a catalog element.
 *
 * @param active desired active flag; {@code null} keeps the current state (new objects are active)
 */
public record CatalogElementCommand(
        String code,
        String nameDe,
        String nameEn,
        String descriptionDe,
        String descriptionEn,
        String referenceVda,
        String referenceAiag,
        String evidenceType,
        Map<String, Object> valueSchema,
        Integer sortOrder,
        Boolean active) {

    CatalogElementValues toValues() {
        return CatalogElementValues.parse(
                code,
                nameDe,
                nameEn,
                descriptionDe,
                descriptionEn,
                referenceVda,
                referenceAiag,
                evidenceType,
                valueSchema,
                sortOrder);
    }

    CatalogElementCommand withCode(String key) {
        return new CatalogElementCommand(
                key,
                nameDe,
                nameEn,
                descriptionDe,
                descriptionEn,
                referenceVda,
                referenceAiag,
                evidenceType,
                valueSchema,
                sortOrder,
                active);
    }
}
