package com.msg.ppap.masterdata.adapter.in.rest;

import com.msg.ppap.masterdata.domain.LevelScheme;
import com.msg.ppap.masterdata.domain.Requirement;
import com.msg.ppap.masterdata.domain.SubmissionLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/** Submission level with its requirement matrix. */
public record SubmissionLevelDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String nameDe,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String nameEn,
        String descriptionDe,
        String descriptionEn,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LevelScheme scheme,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int sortOrder,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean active,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long version,

        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Element code to requirement; missing codes are NOT_REQUIRED")
        Map<String, Requirement> requirements,

        Instant createdAt,
        Instant updatedAt) {

    static SubmissionLevelDto from(SubmissionLevel level) {
        var v = level.values();
        var m = level.metadata();
        return new SubmissionLevelDto(
                level.id(),
                v.code().value(),
                v.nameDe(),
                v.nameEn(),
                v.descriptionDe(),
                v.descriptionEn(),
                v.scheme(),
                v.sortOrder(),
                level.active(),
                m.version(),
                matrix(level),
                m.createdAt(),
                m.updatedAt());
    }

    static Map<String, Requirement> matrix(SubmissionLevel level) {
        var matrix = new TreeMap<String, Requirement>();
        level.values().requirements().forEach((code, requirement) -> matrix.put(code.value(), requirement));
        return matrix;
    }
}
