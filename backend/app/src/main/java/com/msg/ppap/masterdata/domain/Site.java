package com.msg.ppap.masterdata.domain;

import com.msg.ppap.platform.domain.Ids;
import java.util.UUID;

/** Site (plant) of the tenant or of a partner. */
public final class Site extends MasterdataAggregate<SiteValues> {

    private Site(UUID id, SiteValues values, boolean active, Metadata metadata) {
        super(id, values, active, metadata);
    }

    public static Site create(SiteValues values, boolean active) {
        return new Site(Ids.newId(), values, active, null);
    }

    public static Site restore(UUID id, SiteValues values, boolean active, Metadata metadata) {
        return new Site(id, values, active, metadata);
    }

    @Override
    public MasterdataType type() {
        return MasterdataType.SITE;
    }

    @Override
    public String businessKey() {
        return values().bpns().value();
    }
}
