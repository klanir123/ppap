package com.msg.ppap.approval.domain;

import java.util.Objects;
import java.util.UUID;

/** Own part of the case (snapshot of number and names). */
public record PartRef(UUID id, String partNumber, String nameDe, String nameEn) {

    public PartRef {
        Objects.requireNonNull(partNumber, "partNumber");
    }
}
