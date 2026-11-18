package com.msg.ppap.masterdata.adapter.in.rest;

import com.msg.ppap.masterdata.application.PartCommand;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Create or replace a part.
 *
 * @param active {@code null} keeps the current state (new parts are active)
 */
public record PartRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String partNumber,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String nameDe,
        String nameEn,
        String drawingNumber,
        String drawingRevision,
        String material,
        String catenaXId,
        Boolean active) {

    PartCommand toCommand() {
        return new PartCommand(partNumber, nameDe, nameEn, drawingNumber, drawingRevision, material, catenaXId, active);
    }
}
