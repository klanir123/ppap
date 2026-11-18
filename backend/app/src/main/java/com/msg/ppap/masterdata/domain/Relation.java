package com.msg.ppap.masterdata.domain;

import com.msg.ppap.platform.domain.Ids;
import java.util.UUID;

/**
 * Customer-supplier relation from the tenant's point of view: exactly one partner in one role. At most one relation
 * per partner and role.
 */
public final class Relation extends MasterdataAggregate<RelationValues> {

    private Relation(UUID id, RelationValues values, boolean active, Metadata metadata) {
        super(id, values, active, metadata);
    }

    public static Relation create(RelationValues values, boolean active) {
        return new Relation(Ids.newId(), values, active, null);
    }

    public static Relation restore(UUID id, RelationValues values, boolean active, Metadata metadata) {
        return new Relation(id, values, active, metadata);
    }

    @Override
    public MasterdataType type() {
        return MasterdataType.RELATION;
    }

    @Override
    public String businessKey() {
        return values().key();
    }
}
