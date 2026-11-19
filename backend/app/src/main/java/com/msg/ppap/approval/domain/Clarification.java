package com.msg.ppap.approval.domain;

import java.util.Objects;

/**
 * Open question of a received request that must be clarified by the supplier before working on it (UC-04 A1, list
 * of cases to clarify).
 *
 * @param reason why the case needs clarification
 * @param partnerPartNumber the customer's part number that could not be mapped
 */
public record Clarification(Reason reason, String partnerPartNumber) {

    public Clarification {
        Objects.requireNonNull(reason, "reason");
    }

    /** Clarification reasons. */
    public enum Reason {
        /** The customer part number has no part mapping within the relation. */
        PART_NOT_MAPPED
    }

    /** Part not mapped. */
    public static Clarification partNotMapped(String partnerPartNumber) {
        return new Clarification(Reason.PART_NOT_MAPPED, partnerPartNumber);
    }
}
