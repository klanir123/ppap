package com.msg.ppap.platform.security;

/** Realm roles defined per participant realm in Keycloak (ADR-0004), without the {@code ROLE_} prefix. */
public final class Roles {

    /** Quality engineer on the customer side (requests and decides PPAPs). */
    public static final String CUSTOMER_QUALITY_ENGINEER = "CUSTOMER_QUALITY_ENGINEER";

    /** Quality manager on the supplier side (prepares and submits PPAPs). */
    public static final String SUPPLIER_QUALITY_MANAGER = "SUPPLIER_QUALITY_MANAGER";

    /** Maintains parts, partners and catalogues. */
    public static final String MASTERDATA_MANAGER = "MASTERDATA_MANAGER";

    /** Operates the connector (EDC, health, exchange cockpit). */
    public static final String CONNECTOR_ADMIN = "CONNECTOR_ADMIN";

    /** Technical clients (client credentials) integrating backend systems. */
    public static final String SYSTEM_INTEGRATION = "SYSTEM_INTEGRATION";

    /** Prefix Spring Security expects for role authorities. */
    public static final String AUTHORITY_PREFIX = "ROLE_";

    private Roles() {}
}
