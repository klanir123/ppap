package com.msg.ppap.masterdata.adapter.in.rest;

import com.msg.ppap.masterdata.application.PartMappingCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * Create or replace a part mapping. The own part is given by {@code partId} or alternatively {@code partNumber}.
 */
public record PartMappingRequest(
        UUID partId,

        @Schema(description = "Own part number, alternative to partId")
        String partNumber,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String partnerPartNumber,
        String ownSiteBpns,
        String partnerSiteBpns) {

    PartMappingCommand toCommand() {
        return new PartMappingCommand(
                partId == null ? null : partId.toString(), partNumber, partnerPartNumber, ownSiteBpns, partnerSiteBpns);
    }
}
