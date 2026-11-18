package com.msg.ppap.masterdata.application;

import com.msg.ppap.masterdata.domain.SubmissionReasonValues;

/**
 * Raw input for creating or updating a submission reason.
 *
 * @param active desired active flag; {@code null} keeps the current state (new objects are active)
 */
public record SubmissionReasonCommand(
        String code,
        String nameDe,
        String nameEn,
        String descriptionDe,
        String descriptionEn,
        Integer sortOrder,
        Boolean active) {

    SubmissionReasonValues toValues() {
        return SubmissionReasonValues.parse(code, nameDe, nameEn, descriptionDe, descriptionEn, sortOrder);
    }

    SubmissionReasonCommand withCode(String key) {
        return new SubmissionReasonCommand(key, nameDe, nameEn, descriptionDe, descriptionEn, sortOrder, active);
    }
}
