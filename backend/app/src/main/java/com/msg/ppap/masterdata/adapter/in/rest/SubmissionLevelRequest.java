package com.msg.ppap.masterdata.adapter.in.rest;

import com.msg.ppap.masterdata.application.SubmissionLevelCommand;
import com.msg.ppap.masterdata.domain.LevelScheme;
import com.msg.ppap.masterdata.domain.Requirement;
import com.msg.ppap.masterdata.domain.SubmissionLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Create or replace a submission level; also the level format of the catalog export/import.
 *
 * @param scheme defaults to {@code CUSTOM}
 * @param requirements replaces the matrix completely; unknown element codes are rejected
 * @param active {@code null} keeps the current state (new levels are active)
 */
public record SubmissionLevelRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String nameDe,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String nameEn,
        String descriptionDe,
        String descriptionEn,
        LevelScheme scheme,
        Integer sortOrder,
        Map<String, Requirement> requirements,
        Boolean active) {

    SubmissionLevelCommand toCommand() {
        Map<String, String> matrix = new LinkedHashMap<>();
        if (requirements != null) {
            requirements.forEach(
                    (code, requirement) -> matrix.put(code, requirement == null ? null : requirement.name()));
        }
        return new SubmissionLevelCommand(
                code,
                nameDe,
                nameEn,
                descriptionDe,
                descriptionEn,
                scheme == null ? null : scheme.name(),
                sortOrder,
                matrix,
                active);
    }

    static SubmissionLevelRequest from(SubmissionLevel level) {
        var v = level.values();
        return new SubmissionLevelRequest(
                v.code().value(),
                v.nameDe(),
                v.nameEn(),
                v.descriptionDe(),
                v.descriptionEn(),
                v.scheme(),
                v.sortOrder(),
                SubmissionLevelDto.matrix(level),
                level.active());
    }
}
