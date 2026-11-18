package com.msg.ppap.masterdata.domain;

import static com.msg.ppap.masterdata.domain.MasterdataRules.NAME_MAX;
import static com.msg.ppap.masterdata.domain.MasterdataRules.SHORT_MAX;

import com.msg.ppap.platform.domain.Bpnl;
import com.msg.ppap.platform.domain.Bpns;
import com.msg.ppap.platform.domain.Validation;

/**
 * Validated attributes of a {@link Site}.
 *
 * @param bpns site BPNS (business key)
 * @param name site name
 * @param ownerBpnl owning legal entity (own tenant or partner)
 * @param street street and number
 * @param postalCode postal code
 * @param city city
 * @param country ISO 3166-1 alpha-2 country code
 */
public record SiteValues(
        Bpns bpns, String name, Bpnl ownerBpnl, String street, String postalCode, String city, String country) {

    /** Parses and validates raw input (owner existence is checked by the application service). */
    public static SiteValues parse(
            String bpns, String name, String ownerBpnl, String street, String postalCode, String city, String country) {
        var v = new Validation();
        var values = new SiteValues(
                v.required("bpns", bpns, Bpns::new, Bpns.PATTERN),
                v.required("name", name, NAME_MAX),
                v.required("ownerBpnl", ownerBpnl, Bpnl::new, Bpnl.PATTERN),
                v.optional("street", street, NAME_MAX),
                v.optional("postalCode", postalCode, SHORT_MAX),
                v.optional("city", city, NAME_MAX),
                v.optional("country", country, MasterdataRules::country, "ISO 3166-1 alpha-2 code"));
        v.throwIfInvalid();
        return values;
    }
}
