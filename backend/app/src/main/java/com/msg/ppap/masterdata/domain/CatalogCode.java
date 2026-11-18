package com.msg.ppap.masterdata.domain;

import java.util.Objects;

/**
 * Stable code of a catalog entry (element, level, reason), e.g. {@code VDA-PPF-09}. Codes are exchanged with
 * partners (AK-02-5) and therefore immutable in meaning.
 *
 * @param value code matching {@code ^[A-Z0-9][A-Z0-9_-]{1,31}$}
 */
public record CatalogCode(String value) {

    public CatalogCode {
        Objects.requireNonNull(value, "code must not be null");
        if (!MasterdataRules.CATALOG_CODE.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid catalog code: '" + value + "'");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
