package com.msg.ppap.approval.domain;

/** Origin of a case. */
public enum CaseSource {
    /** Created locally by the customer. */
    LOCAL,
    /** Request of a customer without Catena-X access, captured manually by the supplier. */
    MANUAL,
    /** Request received via the dataspace. */
    EXCHANGE,
    /** Migrated historical case (unsigned, read-only history). */
    LEGACY_IMPORT
}
