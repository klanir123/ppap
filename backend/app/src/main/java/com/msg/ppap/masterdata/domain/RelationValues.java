package com.msg.ppap.masterdata.domain;

import static com.msg.ppap.masterdata.domain.MasterdataRules.SHORT_MAX;

import com.msg.ppap.platform.domain.Bpnl;
import com.msg.ppap.platform.domain.Validation;

/**
 * Validated attributes of a {@link Relation}.
 *
 * @param partnerBpnl partner of the relation
 * @param partnerRole role of the partner ({@code CUSTOMER}: we deliver, {@code SUPPLIER}: we source)
 * @param exchangeMode exchange mode for submissions
 * @param policyProfile policy profile name (phase 6)
 */
public record RelationValues(
        Bpnl partnerBpnl, PartnerRole partnerRole, ExchangeMode exchangeMode, String policyProfile) {

    /** Default exchange mode (ADR-0002). */
    public static final ExchangeMode DEFAULT_EXCHANGE_MODE = ExchangeMode.PUSH_NOTIFY_PULL_PAYLOAD;

    /** Default policy profile. */
    public static final String DEFAULT_POLICY_PROFILE = "default";

    /** Parses and validates raw input (partner existence and role are checked by the application service). */
    public static RelationValues parse(
            String partnerBpnl, String partnerRole, String exchangeMode, String policyProfile) {
        var v = new Validation();
        String profile = v.optional("policyProfile", policyProfile, SHORT_MAX);
        var values = new RelationValues(
                v.required("partnerBpnl", partnerBpnl, Bpnl::new, Bpnl.PATTERN),
                v.requiredEnum("partnerRole", partnerRole, PartnerRole.class),
                v.optionalEnum("exchangeMode", exchangeMode, ExchangeMode.class, DEFAULT_EXCHANGE_MODE),
                profile == null ? DEFAULT_POLICY_PROFILE : profile);
        v.throwIfInvalid();
        return values;
    }

    /** Business key {@code <partnerBpnl>/<partnerRole>}. */
    public String key() {
        return partnerBpnl.value() + "/" + partnerRole.name();
    }
}
