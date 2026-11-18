package com.msg.ppap.masterdata.domain;

import com.msg.ppap.platform.domain.Ids;
import java.util.UUID;

/** Business partner (customer and/or supplier) of the tenant. */
public final class Partner extends MasterdataAggregate<PartnerValues> {

    private Partner(UUID id, PartnerValues values, boolean active, Metadata metadata) {
        super(id, values, active, metadata);
    }

    public static Partner create(PartnerValues values, boolean active) {
        return new Partner(Ids.newId(), values, active, null);
    }

    public static Partner restore(UUID id, PartnerValues values, boolean active, Metadata metadata) {
        return new Partner(id, values, active, metadata);
    }

    /** Active and addressable via the dataspace (AK-01-5). */
    public boolean exchangeReady() {
        return active() && values().addressable();
    }

    /** Whether the partner has the given role. */
    public boolean hasRole(PartnerRole role) {
        return values().roles().contains(role);
    }

    @Override
    public MasterdataType type() {
        return MasterdataType.PARTNER;
    }

    @Override
    public String businessKey() {
        return values().bpnl().value();
    }
}
