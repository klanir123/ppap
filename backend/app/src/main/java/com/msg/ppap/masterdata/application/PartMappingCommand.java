package com.msg.ppap.masterdata.application;

/**
 * Raw input for creating or updating a part mapping. The own part is given by {@code partId} or, alternatively,
 * {@code partNumber}.
 */
public record PartMappingCommand(
        String partId, String partNumber, String partnerPartNumber, String ownSiteBpns, String partnerSiteBpns) {}
