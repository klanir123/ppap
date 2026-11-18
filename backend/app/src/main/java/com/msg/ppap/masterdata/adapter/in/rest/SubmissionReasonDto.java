package com.msg.ppap.masterdata.adapter.in.rest;

import com.msg.ppap.masterdata.domain.SubmissionReason;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/** Reason for a submission. */
public record SubmissionReasonDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String nameDe,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String nameEn,
        String descriptionDe,
        String descriptionEn,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int sortOrder,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean active,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long version,
        Instant createdAt,
        Instant updatedAt) {

    static SubmissionReasonDto from(SubmissionReason reason) {
        var v = reason.values();
        var m = reason.metadata();
        return new SubmissionReasonDto(
                reason.id(),
                v.code().value(),
                v.nameDe(),
                v.nameEn(),
                v.descriptionDe(),
                v.descriptionEn(),
                v.sortOrder(),
                reason.active(),
                m.version(),
                m.createdAt(),
                m.updatedAt());
    }
}
