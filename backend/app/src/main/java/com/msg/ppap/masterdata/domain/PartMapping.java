package com.msg.ppap.masterdata.domain;

import com.msg.ppap.platform.domain.Ids;
import java.util.UUID;

/**
 * Mapping between an own part and the partner's part number within a relation. Unique per (relation, part) and
 * (relation, partner part number). Mappings are deleted physically.
 */
public final class PartMapping extends MasterdataAggregate<PartMappingValues> {

    private PartMapping(UUID id, PartMappingValues values, Metadata metadata) {
        super(id, values, true, metadata);
    }

    public static PartMapping create(PartMappingValues values) {
        return new PartMapping(Ids.newId(), values, null);
    }

    public static PartMapping restore(UUID id, PartMappingValues values, Metadata metadata) {
        return new PartMapping(id, values, metadata);
    }

    /** Replaces the values (mappings have no active flag). */
    public void update(PartMappingValues newValues) {
        update(newValues, true);
    }

    /** Whether the given values would modify the mapping. */
    public boolean wouldChange(PartMappingValues newValues) {
        return wouldChange(newValues, true);
    }

    @Override
    public MasterdataType type() {
        return MasterdataType.PART_MAPPING;
    }

    @Override
    public String businessKey() {
        return values().relationId() + "/" + values().partnerPartNumber();
    }
}
