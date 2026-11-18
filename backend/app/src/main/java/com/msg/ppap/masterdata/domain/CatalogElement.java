package com.msg.ppap.masterdata.domain;

import com.msg.ppap.platform.domain.Ids;
import java.util.UUID;

/** Submission element of the PPAP/PPF catalog (UC-02), e.g. {@code VDA-PPF-09}. */
public final class CatalogElement extends MasterdataAggregate<CatalogElementValues> {

    private CatalogElement(UUID id, CatalogElementValues values, boolean active, Metadata metadata) {
        super(id, values, active, metadata);
    }

    public static CatalogElement create(CatalogElementValues values, boolean active) {
        return new CatalogElement(Ids.newId(), values, active, null);
    }

    public static CatalogElement restore(UUID id, CatalogElementValues values, boolean active, Metadata metadata) {
        return new CatalogElement(id, values, active, metadata);
    }

    @Override
    public MasterdataType type() {
        return MasterdataType.CATALOG_ELEMENT;
    }

    @Override
    public String businessKey() {
        return values().code().value();
    }
}
