/**
 * JPA persistence of master data. Entities are separate from the domain model (the domain has no persistence
 * annotations); every query is scoped by {@code tenant_id} from {@link com.msg.ppap.identity.TenantContext}.
 */
package com.msg.ppap.masterdata.adapter.out.jpa;
