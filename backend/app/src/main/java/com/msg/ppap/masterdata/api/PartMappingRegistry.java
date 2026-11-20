package com.msg.ppap.masterdata.api;

import java.util.UUID;

/**
 * Write access for downstream modules: registers a part mapping found while clarifying a received request (UC-04
 * A1). Goes through the regular master data use case (validation, audit event).
 */
public interface PartMappingRegistry {

    /**
     * Creates the mapping {@code partId ↔ partnerPartNumber} within the relation.
     *
     * @throws com.msg.ppap.platform.domain.PpapException on validation errors or duplicates
     */
    void registerMapping(UUID relationId, UUID partId, String partnerPartNumber);

    /**
     * Creates the mapping or replaces the partner part number of the existing mapping of this part in the relation
     * (assign-part with {@code replaceExistingMapping}, audited as update).
     */
    void replaceMapping(UUID relationId, UUID partId, String partnerPartNumber);
}
