package com.msg.ppap.masterdata.domain;

import com.msg.ppap.platform.domain.Ids;
import java.util.UUID;

/** Own part of the tenant (customer part number for the OEM, own part number for suppliers). */
public final class Part extends MasterdataAggregate<PartValues> {

    private Part(UUID id, PartValues values, boolean active, Metadata metadata) {
        super(id, values, active, metadata);
    }

    /** New, active part. */
    public static Part create(PartValues values, boolean active) {
        return new Part(Ids.newId(), values, active, null);
    }

    /** Reconstitutes a persisted part. */
    public static Part restore(UUID id, PartValues values, boolean active, Metadata metadata) {
        return new Part(id, values, active, metadata);
    }

    @Override
    public MasterdataType type() {
        return MasterdataType.PART;
    }

    @Override
    public String businessKey() {
        return values().partNumber().value();
    }
}
