package com.msg.ppap.masterdata.adapter.in.rest;

import com.msg.ppap.masterdata.domain.Part;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/** Own part of the tenant. */
public record PartDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String partNumber,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String nameDe,
        String nameEn,
        String drawingNumber,
        String drawingRevision,
        String material,
        @Schema(description = "Catena-X id (urn:uuid:…)") String catenaXId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean active,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long version,
        Instant createdAt,
        Instant updatedAt) {

    static PartDto from(Part part) {
        var v = part.values();
        var m = part.metadata();
        return new PartDto(
                part.id(),
                v.partNumber().value(),
                v.nameDe(),
                v.nameEn(),
                v.drawingNumber() == null ? null : v.drawingNumber().value(),
                v.drawingRevision(),
                v.material(),
                v.catenaXId(),
                part.active(),
                m.version(),
                m.createdAt(),
                m.updatedAt());
    }
}
