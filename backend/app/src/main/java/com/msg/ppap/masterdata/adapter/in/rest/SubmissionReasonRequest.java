package com.msg.ppap.masterdata.adapter.in.rest;

import com.msg.ppap.masterdata.application.SubmissionReasonCommand;
import com.msg.ppap.masterdata.domain.SubmissionReason;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Create or replace a submission reason; also the reason format of the catalog export/import.
 *
 * @param active {@code null} keeps the current state (new reasons are active)
 */
public record SubmissionReasonRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String nameDe,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String nameEn,
        String descriptionDe,
        String descriptionEn,
        Integer sortOrder,
        Boolean active) {

    SubmissionReasonCommand toCommand() {
        return new SubmissionReasonCommand(code, nameDe, nameEn, descriptionDe, descriptionEn, sortOrder, active);
    }

    static SubmissionReasonRequest from(SubmissionReason reason) {
        var v = reason.values();
        return new SubmissionReasonRequest(
                v.code().value(),
                v.nameDe(),
                v.nameEn(),
                v.descriptionDe(),
                v.descriptionEn(),
                v.sortOrder(),
                reason.active());
    }
}
