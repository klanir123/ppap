package com.msg.ppap.masterdata.application;

import com.msg.ppap.masterdata.domain.SubmissionLevelValues;
import java.util.Map;

/**
 * Raw input for creating or updating a submission level.
 *
 * @param requirements element code to requirement name; replaces the matrix completely
 * @param active desired active flag; {@code null} keeps the current state (new objects are active)
 */
public record SubmissionLevelCommand(
        String code,
        String nameDe,
        String nameEn,
        String descriptionDe,
        String descriptionEn,
        String scheme,
        Integer sortOrder,
        Map<String, String> requirements,
        Boolean active) {

    SubmissionLevelValues toValues() {
        return SubmissionLevelValues.parse(
                code, nameDe, nameEn, descriptionDe, descriptionEn, scheme, sortOrder, requirements);
    }

    SubmissionLevelCommand withCode(String key) {
        return new SubmissionLevelCommand(
                key, nameDe, nameEn, descriptionDe, descriptionEn, scheme, sortOrder, requirements, active);
    }
}
