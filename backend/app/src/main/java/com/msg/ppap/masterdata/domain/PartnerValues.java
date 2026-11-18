package com.msg.ppap.masterdata.domain;

import static com.msg.ppap.masterdata.domain.MasterdataRules.EMAIL_MAX;
import static com.msg.ppap.masterdata.domain.MasterdataRules.NAME_MAX;
import static com.msg.ppap.masterdata.domain.MasterdataRules.URL_MAX;

import com.msg.ppap.platform.domain.Bpnl;
import com.msg.ppap.platform.domain.FieldViolation;
import com.msg.ppap.platform.domain.Validation;
import com.msg.ppap.platform.domain.ValidationException;
import com.msg.ppap.platform.domain.ViolationCodes;
import java.net.URI;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Validated attributes of a {@link Partner}.
 *
 * @param bpnl partner BPNL (business key)
 * @param name company name
 * @param roles roles of the partner from the tenant's point of view (at least one)
 * @param dspEndpoint DSP endpoint of the partner's EDC
 * @param did decentralized identifier of the partner
 * @param contactEmail functional mailbox
 */
public record PartnerValues(
        Bpnl bpnl, String name, Set<PartnerRole> roles, URI dspEndpoint, String did, String contactEmail) {

    public PartnerValues {
        roles = roles == null || roles.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(roles));
    }

    /**
     * Parses and validates raw input.
     *
     * @param ownTenant BPNL of the own tenant, which must not be registered as partner
     */
    public static PartnerValues parse(
            String bpnl,
            String name,
            Collection<String> roles,
            String dspEndpoint,
            String did,
            String contactEmail,
            Bpnl ownTenant) {
        var v = new Validation();
        var values = new PartnerValues(
                v.required("bpnl", bpnl, Bpnl::new, Bpnl.PATTERN),
                v.required("name", name, NAME_MAX),
                v.requiredEnumSet("roles", roles, PartnerRole.class),
                v.optional(
                        "dspEndpoint",
                        trimmedMax(v, "dspEndpoint", dspEndpoint),
                        MasterdataRules::httpUrl,
                        "absolute http(s) URL"),
                v.optionalMatching("did", did, URL_MAX, MasterdataRules.DID, "did:<method>:<id>"),
                v.optionalMatching("contactEmail", contactEmail, EMAIL_MAX, MasterdataRules.EMAIL, "e-mail address"));
        if (values.bpnl() != null && values.bpnl().equals(ownTenant)) {
            throw new ValidationException(
                    ViolationCodes.SELF_REFERENCE,
                    List.of(new FieldViolation(
                            "bpnl", ViolationCodes.SELF_REFERENCE, "the own tenant cannot be a partner")));
        }
        v.throwIfInvalid();
        return values;
    }

    /** Whether the partner can be addressed via the dataspace (DSP endpoint and DID known). */
    public boolean addressable() {
        return dspEndpoint != null && did != null;
    }

    private static String trimmedMax(Validation v, String field, String raw) {
        String value = Validation.trimToNull(raw);
        if (value != null && value.length() > URL_MAX) {
            v.add(field, ViolationCodes.TOO_LONG, "must be at most " + URL_MAX + " characters");
            return null;
        }
        return value;
    }
}
