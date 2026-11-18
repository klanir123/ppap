package com.msg.ppap.masterdata.domain;

import static com.msg.ppap.masterdata.domain.MasterdataRules.NAME_MAX;
import static com.msg.ppap.masterdata.domain.MasterdataRules.TEXT_MAX;

import com.msg.ppap.platform.domain.Validation;
import com.msg.ppap.platform.domain.ViolationCodes;
import java.util.HashMap;
import java.util.Map;

/**
 * Validated attributes of a {@link SubmissionLevel}.
 *
 * @param code level code (business key)
 * @param nameDe German name
 * @param nameEn English name
 * @param descriptionDe German description
 * @param descriptionEn English description
 * @param scheme origin of the level
 * @param sortOrder display order
 * @param requirements element code to requirement; normalized: {@code NOT_REQUIRED} entries are omitted since a
 *     missing code means {@code NOT_REQUIRED}
 */
public record SubmissionLevelValues(
        CatalogCode code,
        String nameDe,
        String nameEn,
        String descriptionDe,
        String descriptionEn,
        LevelScheme scheme,
        int sortOrder,
        Map<CatalogCode, Requirement> requirements) {

    public SubmissionLevelValues {
        var normalized = new HashMap<CatalogCode, Requirement>();
        if (requirements != null) {
            requirements.forEach((elementCode, requirement) -> {
                if (elementCode != null && requirement != null && requirement != Requirement.NOT_REQUIRED) {
                    normalized.put(elementCode, requirement);
                }
            });
        }
        requirements = Map.copyOf(normalized);
    }

    /** Requirement of an element at this level ({@code NOT_REQUIRED} if not listed). */
    public Requirement requirementOf(CatalogCode elementCode) {
        return requirements.getOrDefault(elementCode, Requirement.NOT_REQUIRED);
    }

    /** Parses and validates raw input; existence of element codes is checked by the application service. */
    public static SubmissionLevelValues parse(
            String code,
            String nameDe,
            String nameEn,
            String descriptionDe,
            String descriptionEn,
            String scheme,
            Integer sortOrder,
            Map<String, String> requirements) {
        var v = new Validation();
        var matrix = new HashMap<CatalogCode, Requirement>();
        if (requirements != null) {
            requirements.forEach((rawCode, rawRequirement) -> {
                String field = "requirements." + rawCode;
                CatalogCode elementCode = null;
                try {
                    elementCode = new CatalogCode(rawCode == null ? "" : rawCode.strip());
                } catch (IllegalArgumentException ex) {
                    v.add(field, ViolationCodes.INVALID_FORMAT, "invalid element code");
                }
                Requirement requirement = v.requiredEnum(field, rawRequirement, Requirement.class);
                if (elementCode != null && requirement != null) {
                    matrix.put(elementCode, requirement);
                }
            });
        }
        var values = new SubmissionLevelValues(
                v.required("code", code, CatalogCode::new, MasterdataRules.CATALOG_CODE.pattern()),
                v.required("nameDe", nameDe, NAME_MAX),
                v.required("nameEn", nameEn, NAME_MAX),
                v.optional("descriptionDe", descriptionDe, TEXT_MAX),
                v.optional("descriptionEn", descriptionEn, TEXT_MAX),
                v.optionalEnum("scheme", scheme, LevelScheme.class, LevelScheme.CUSTOM),
                sortOrder == null ? 0 : sortOrder,
                matrix);
        v.throwIfInvalid();
        return values;
    }
}
