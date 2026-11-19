package com.msg.ppap.approval.domain;

/** User roles relevant for approval cases (Keycloak realm roles) plus the internal role of inbound processing. */
public enum UserRole {
    CUSTOMER_QUALITY_ENGINEER,
    SUPPLIER_QUALITY_MANAGER,
    SYSTEM_INTEGRATION,
    /** Internal: processing of partner messages (never granted to tokens). */
    EXCHANGE
}
