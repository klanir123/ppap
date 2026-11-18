package com.msg.ppap.identity;

import com.msg.ppap.identity.config.TenantProperties;
import com.msg.ppap.platform.domain.Bpnl;
import org.springframework.stereotype.Component;

/**
 * Provides the tenant of the current request. Every repository sets and filters {@code tenant_id} through this
 * component (ADR-0004).
 *
 * <p>Prototype: exactly one tenant per instance ({@code ppap.tenant.bpnl}). Hosting several tenants later resolves
 * the tenant from the token issuer here, without changes to the repositories.
 *
 * <p>TODO(ADR-0004): enable PostgreSQL row-level security ({@code app.tenant_id} session variable) as second line
 * of defence.
 */
@Component
public class TenantContext {

    private final TenantProperties tenant;
    private final Bpnl bpnl;

    public TenantContext(TenantProperties tenant) {
        this.tenant = tenant;
        this.bpnl = tenant.businessPartnerNumber();
    }

    /** Tenant id (BPNL) used as {@code tenant_id}. */
    public String tenantId() {
        return bpnl.value();
    }

    /** BPNL of the current tenant. */
    public Bpnl bpnl() {
        return bpnl;
    }

    /** Display name of the current tenant. */
    public String tenantName() {
        return tenant.name();
    }

    /** Whether the given BPNL is the current tenant. */
    public boolean isOwnTenant(Bpnl candidate) {
        return bpnl.equals(candidate);
    }
}
