package com.msg.ppap.masterdata.application;

import com.msg.ppap.masterdata.domain.SiteValues;

/**
 * Raw input for creating or updating a site.
 *
 * @param active desired active flag; {@code null} keeps the current state (new objects are active)
 */
public record SiteCommand(
        String bpns,
        String name,
        String ownerBpnl,
        String street,
        String postalCode,
        String city,
        String country,
        Boolean active) {

    SiteValues toValues() {
        return SiteValues.parse(bpns, name, ownerBpnl, street, postalCode, city, country);
    }

    SiteCommand withBpns(String key) {
        return new SiteCommand(key, name, ownerBpnl, street, postalCode, city, country, active);
    }
}
