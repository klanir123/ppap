package com.msg.ppap.masterdata.domain;

import static com.msg.ppap.masterdata.domain.MasterdataRules.NAME_MAX;
import static com.msg.ppap.masterdata.domain.MasterdataRules.TEXT_MAX;

import com.msg.ppap.platform.domain.Validation;

/**
 * Validated attributes of a {@link SubmissionReason}.
 *
 * @param code reason code (business key)
 * @param nameDe German name
 * @param nameEn English name
 * @param descriptionDe German description
 * @param descriptionEn English description
 * @param sortOrder display order
 */
public record SubmissionReasonValues(
        CatalogCode code, String nameDe, String nameEn, String descriptionDe, String descriptionEn, int sortOrder) {

    /** Parses and validates raw input. */
    public static SubmissionReasonValues parse(
            String code, String nameDe, String nameEn, String descriptionDe, String descriptionEn, Integer sortOrder) {
        var v = new Validation();
        var values = new SubmissionReasonValues(
                v.required("code", code, CatalogCode::new, MasterdataRules.CATALOG_CODE.pattern()),
                v.required("nameDe", nameDe, NAME_MAX),
                v.required("nameEn", nameEn, NAME_MAX),
                v.optional("descriptionDe", descriptionDe, TEXT_MAX),
                v.optional("descriptionEn", descriptionEn, TEXT_MAX),
                sortOrder == null ? 0 : sortOrder);
        v.throwIfInvalid();
        return values;
    }
}
