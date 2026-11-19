package com.msg.ppap.approval.domain;

/** Role of the own tenant in a case. */
public enum CaseRole {
    CUSTOMER,
    SUPPLIER;

    /** Role of the partner from the own point of view (as stored on the master data relation). */
    public CaseRole partnerRole() {
        return this == CUSTOMER ? SUPPLIER : CUSTOMER;
    }
}
