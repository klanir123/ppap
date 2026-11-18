package com.msg.ppap.masterdata.application;

import com.msg.ppap.masterdata.domain.PartnerValues;
import com.msg.ppap.platform.domain.Bpnl;
import java.util.Collection;

/**
 * Raw input for creating or updating a partner.
 *
 * @param roles role names ({@code CUSTOMER}, {@code SUPPLIER})
 * @param active desired active flag; {@code null} keeps the current state (new objects are active)
 */
public record PartnerCommand(
        String bpnl,
        String name,
        Collection<String> roles,
        String dspEndpoint,
        String did,
        String contactEmail,
        Boolean active) {

    PartnerValues toValues(Bpnl ownTenant) {
        return PartnerValues.parse(bpnl, name, roles, dspEndpoint, did, contactEmail, ownTenant);
    }

    PartnerCommand withBpnl(String key) {
        return new PartnerCommand(key, name, roles, dspEndpoint, did, contactEmail, active);
    }
}
