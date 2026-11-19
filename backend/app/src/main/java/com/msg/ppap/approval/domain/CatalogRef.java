package com.msg.ppap.approval.domain;

import java.util.Objects;

/** Snapshot of a catalogue entry (reason, level) at request time. */
public record CatalogRef(String code, String nameDe, String nameEn) {

    public CatalogRef {
        Objects.requireNonNull(code, "code");
        nameDe = nameDe == null ? code : nameDe;
        nameEn = nameEn == null ? nameDe : nameEn;
    }
}
