package com.msg.ppap.exchange.application;

import com.msg.ppap.exchange.application.port.out.DataspaceConnector;
import com.msg.ppap.exchange.application.port.out.PolicySpec;
import com.msg.ppap.exchange.config.EdcProperties;
import com.msg.ppap.masterdata.api.MasterdataQueries;
import com.msg.ppap.masterdata.api.MasterdataQueries.PolicyProfileInfo;
import com.msg.ppap.platform.domain.NotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Preview of the ODRL policies (JSON-LD, exactly as sent to the EDC management API) that a policy profile produces
 * (docs/api/cascade-transparency-api.md §3, {@code GET /policy-profiles/{id}/preview}).
 */
@Service
public class PolicyPreviewService {

    /** Placeholder of the partner BPNL in the preview of per-partner access policies. */
    static final String PARTNER_PLACEHOLDER = "<BPNL>";

    private final MasterdataQueries masterdata;
    private final DataspaceConnector connector;
    private final EdcProperties edc;

    public PolicyPreviewService(MasterdataQueries masterdata, DataspaceConnector connector, EdcProperties edc) {
        this.masterdata = masterdata;
        this.connector = connector;
        this.edc = edc;
    }

    /** Policies of the profile: usage, access of per-partner assets and – for {@code default} – shared assets. */
    public Preview preview(UUID profileId) {
        PolicyProfileInfo profile = masterdata
                .findPolicyProfile(profileId)
                .orElseThrow(() -> new NotFoundException("Policy profile", profileId));
        List<PolicyPreview> policies = new ArrayList<>();
        PolicySpec usage = OfferPolicies.usage(profile);
        policies.add(new PolicyPreview("USAGE", usage.id(), connector.renderPolicy(usage)));
        PolicySpec partner = OfferPolicies.partnerAccess(profile, PARTNER_PLACEHOLDER);
        policies.add(new PolicyPreview("ACCESS_PARTNER", partner.id(), connector.renderPolicy(partner)));
        if ("default".equals(profile.code())) {
            TreeSet<String> partners = new TreeSet<>();
            masterdata.exchangePartners().forEach(p -> partners.add(p.bpnl()));
            PolicySpec shared = OfferPolicies.sharedAccess(profile, edc.policy().partnerGroup(), partners);
            policies.add(new PolicyPreview("ACCESS_SHARED", shared.id(), connector.renderPolicy(shared)));
        }
        List<String> relations = masterdata.relations().stream()
                .filter(r -> profile.code().equals(r.policyProfile()))
                .map(r -> r.partnerBpnl() + "/" + r.partnerRole())
                .toList();
        return new Preview(profile.id(), profile.code(), profile.version(), relations, policies);
    }

    /**
     * Preview of a profile.
     *
     * @param relations relations using the profile ({@code <BPNL>/<role>})
     */
    public record Preview(
            UUID profileId, String code, long version, List<String> relations, List<PolicyPreview> policies) {}

    /**
     * One generated policy definition.
     *
     * @param role {@code USAGE}, {@code ACCESS_PARTNER} (per-partner assets) or {@code ACCESS_SHARED} (notification,
     *     public keys, DTR; profile {@code default} only)
     */
    public record PolicyPreview(String role, String policyId, Map<String, Object> policy) {}
}
