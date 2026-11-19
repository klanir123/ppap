package com.msg.ppap.approval.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Own part of the case (snapshot of number and names). Id and number are both {@code null} only for received
 * requests whose part could not be mapped yet ({@link Clarification}).
 */
public record PartRef(UUID id, String partNumber, String nameDe, String nameEn) {

    public PartRef {
        if (id != null) {
            Objects.requireNonNull(partNumber, "partNumber");
        }
    }

    /** Placeholder for a part that is not mapped yet (names as known to the customer). */
    public static PartRef unmapped(String nameDe) {
        return new PartRef(null, null, nameDe, null);
    }

    /** Whether the part is known. */
    public boolean mapped() {
        return partNumber != null;
    }
}
