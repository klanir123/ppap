package com.msg.ppap.masterdata.adapter.in.rest;

import com.msg.ppap.masterdata.application.PartMappingView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/** Mapping of an own part to the partner's part number within a relation. */
public record PartMappingDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID relationId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID partId,
        @Schema(description = "Own part number (read-only)") String partNumber,

        @Schema(description = "German name of the own part (read-only)")
        String partNameDe,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String partnerPartNumber,
        String ownSiteBpns,
        String partnerSiteBpns,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long version,
        Instant createdAt,
        Instant updatedAt) {

    static PartMappingDto from(PartMappingView view) {
        var mapping = view.mapping();
        var v = mapping.values();
        var m = mapping.metadata();
        return new PartMappingDto(
                mapping.id(),
                v.relationId(),
                v.partId(),
                view.partNumber(),
                view.partNameDe(),
                v.partnerPartNumber().value(),
                v.ownSiteBpns() == null ? null : v.ownSiteBpns().value(),
                v.partnerSiteBpns() == null ? null : v.partnerSiteBpns().value(),
                m.version(),
                m.createdAt(),
                m.updatedAt());
    }
}
