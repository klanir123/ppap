package com.msg.ppap.exchange.application;

import com.msg.ppap.exchange.application.port.out.PolicySpec;
import com.msg.ppap.exchange.config.EdcProperties;
import com.msg.ppap.masterdata.api.MasterdataQueries.PolicyProfileInfo;
import com.msg.ppap.semantics.api.CanonicalJson;
import com.msg.ppap.semantics.api.PayloadHash;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Mapping of policy profiles (master data, docs/api/cascade-transparency-api.md §3) to the ODRL policies of the own
 * offers (CX-0152 2025/9):
 *
 * <ul>
 *   <li><b>usage</b>: {@code FrameworkAgreement eq <agreement>} + {@code UsagePurpose isAnyOf [<purposes>]} + the
 *       additional constraints of the profile;
 *   <li><b>access of the shared assets</b> (notification, public keys, DTR; profile {@code default}): {@code
 *       BusinessPartnerGroup isAnyOf [ppap-partners]} ({@code BPN_GROUP}) or {@code BusinessPartnerNumber isAnyOf
 *       [<partners>]} ({@code BPN});
 *   <li><b>access of the per-partner assets</b> ({@code ppap-submission-<BPNL>}, {@code ppap-documents-<BPNL>}): always
 *       {@code BusinessPartnerNumber isAnyOf [<BPNL>]} – the asset belongs to exactly that partner;
 *   <li>{@code membershipRequired}: access additionally requires {@code Membership eq active}.
 * </ul>
 *
 * Policy ids are content-addressed ({@link #versioned}), because policies referenced by a contract definition cannot
 * be changed in Tractus-X EDC 0.13: a changed profile yields new ids and the contract definitions are re-pointed.
 */
public final class OfferPolicies {

    static final String MEMBERSHIP = "Membership";

    private OfferPolicies() {}

    /** Usage policy of a profile ({@code ppap-usage-<code>-<hash8>}). */
    public static PolicySpec usage(PolicyProfileInfo profile) {
        List<PolicySpec.Constraint> constraints = new ArrayList<>();
        constraints.add(new PolicySpec.Constraint("FrameworkAgreement", "eq", profile.frameworkAgreement()));
        constraints.add(new PolicySpec.Constraint("UsagePurpose", "isAnyOf", List.copyOf(profile.usagePurposes())));
        profile.additionalConstraints()
                .forEach(c ->
                        constraints.add(new PolicySpec.Constraint(c.leftOperand(), c.operator(), c.rightOperand())));
        return versioned(
                new PolicySpec(ExchangeAssets.USAGE_POLICY + "-" + idPart(profile.code()), "use", constraints));
    }

    /** Access policy of the shared assets ({@code ppap-access-partners-<hash8>}). */
    public static PolicySpec sharedAccess(PolicyProfileInfo profile, String group, Collection<String> partners) {
        List<PolicySpec.Constraint> constraints = membership(profile);
        if ("BPN".equals(profile.accessType()) && !partners.isEmpty()) {
            constraints.add(new PolicySpec.Constraint(
                    "BusinessPartnerNumber", "isAnyOf", List.copyOf(new TreeSet<>(partners))));
        } else {
            constraints.add(new PolicySpec.Constraint("BusinessPartnerGroup", "isAnyOf", List.of(group)));
        }
        return versioned(new PolicySpec(ExchangeAssets.ACCESS_POLICY, "access", constraints));
    }

    /** Access policy of the assets offered to exactly one partner ({@code ppap-access-bpn-<BPNL>-<hash8>}). */
    public static PolicySpec partnerAccess(PolicyProfileInfo profile, String bpnl) {
        List<PolicySpec.Constraint> constraints = membership(profile);
        constraints.add(new PolicySpec.Constraint("BusinessPartnerNumber", "isAnyOf", List.of(bpnl)));
        return versioned(new PolicySpec(ExchangeAssets.BPN_ACCESS_POLICY_PREFIX + bpnl, "access", constraints));
    }

    /**
     * Fallback profile from the configuration ({@code ppap.edc.policy.*}) if the master data has no profile {@code
     * default} (behaviour of phases 4/5: no explicit membership constraint).
     */
    public static PolicyProfileInfo fallback(EdcProperties.Policy policy) {
        return new PolicyProfileInfo(
                new UUID(0, 0),
                "default",
                "Configuration",
                "BPN_GROUP",
                false,
                policy.frameworkAgreement(),
                List.of(policy.usagePurpose()),
                List.of(),
                true,
                0);
    }

    /**
     * Policy with a content-addressed id ({@code <base>-<8 hex of SHA-256 over the canonical spec>}): policy
     * definitions referenced by a contract definition cannot be changed in the EDC, so a changed policy gets a new id
     * and the contract definitions are switched to it.
     */
    public static PolicySpec versioned(PolicySpec spec) {
        String hash = PayloadHash.sha256Hex(CanonicalJson.canonicalize(Map.of(
                "action",
                spec.action(),
                "constraints",
                spec.constraints().stream()
                        .map(c -> Map.of(
                                "leftOperand", c.leftOperand(),
                                "operator", c.operator(),
                                "rightOperand", c.rightOperand()))
                        .toList())));
        return new PolicySpec(spec.id() + "-" + hash.substring(0, 8), spec.action(), spec.constraints());
    }

    private static List<PolicySpec.Constraint> membership(PolicyProfileInfo profile) {
        List<PolicySpec.Constraint> constraints = new ArrayList<>();
        if (profile.membershipRequired()) {
            constraints.add(new PolicySpec.Constraint(MEMBERSHIP, "eq", "active"));
        }
        return constraints;
    }

    private static String idPart(String code) {
        return code.replaceAll("[^A-Za-z0-9._\\-]", "-");
    }
}
