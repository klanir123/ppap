package com.msg.ppap.exchange.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.msg.ppap.exchange.application.port.out.PolicySpec;
import com.msg.ppap.exchange.config.EdcProperties;
import com.msg.ppap.masterdata.api.MasterdataQueries.ConstraintInfo;
import com.msg.ppap.masterdata.api.MasterdataQueries.PolicyProfileInfo;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Policy profile → ODRL mapping (docs/api/cascade-transparency-api.md §3). */
class OfferPoliciesTest {

    static PolicyProfileInfo profile(String code, String access, boolean membership, List<String> purposes) {
        return new PolicyProfileInfo(
                UUID.randomUUID(),
                code,
                code,
                access,
                membership,
                "DataExchangeGovernance:1.0",
                purposes,
                List.of(new ConstraintInfo("ContractReference", "eq", "PPAP-2026")),
                true,
                1);
    }

    @Test
    void usagePolicyCarriesAgreementPurposesAndAdditionalConstraints() {
        PolicySpec usage = OfferPolicies.usage(profile("quality", "BPN_GROUP", true, List.of("cx.quality.base:1")));
        assertThat(usage.id()).matches("^ppap-usage-quality-[0-9a-f]{8}$");
        assertThat(usage.action()).isEqualTo("use");
        assertThat(usage.constraints())
                .extracting(PolicySpec.Constraint::leftOperand)
                .containsExactly("FrameworkAgreement", "UsagePurpose", "ContractReference");
        assertThat(usage.constraints().get(1).rightOperand()).isEqualTo(List.of("cx.quality.base:1"));
        // content-addressed: another purpose yields another id, the same content the same id
        PolicySpec other =
                OfferPolicies.usage(profile("quality", "BPN_GROUP", true, List.of("msg.ppap.productApproval:1")));
        assertThat(other.id()).isNotEqualTo(usage.id()).startsWith("ppap-usage-quality-");
        assertThat(OfferPolicies.usage(profile("quality", "BPN", false, List.of("cx.quality.base:1")))
                        .id())
                .isEqualTo(usage.id());
    }

    @Test
    void accessPoliciesForSharedAndPerPartnerAssets() {
        PolicySpec group = OfferPolicies.sharedAccess(
                profile("default", "BPN_GROUP", true, List.of("p:1")), "ppap-partners", Set.of("BPNL000000TIER02"));
        assertThat(group.id()).startsWith("ppap-access-partners-");
        assertThat(group.constraints())
                .extracting(PolicySpec.Constraint::leftOperand)
                .containsExactly("Membership", "BusinessPartnerGroup");
        PolicySpec bpn = OfferPolicies.sharedAccess(
                profile("default", "BPN", false, List.of("p:1")),
                "ppap-partners",
                Set.of("BPNL000000TIER02", "BPNL00000000OEM1"));
        assertThat(bpn.constraints()).singleElement().satisfies(c -> {
            assertThat(c.leftOperand()).isEqualTo("BusinessPartnerNumber");
            assertThat(c.rightOperand()).isEqualTo(List.of("BPNL00000000OEM1", "BPNL000000TIER02"));
        });
        // BPN without partners falls back to the (empty) group instead of an empty list
        assertThat(OfferPolicies.sharedAccess(profile("default", "BPN", false, List.of("p:1")), "g", Set.of())
                        .constraints()
                        .getFirst()
                        .leftOperand())
                .isEqualTo("BusinessPartnerGroup");
        PolicySpec partner = OfferPolicies.partnerAccess(profile("x", "BPN_GROUP", true, List.of("p:1")), "BPNL1");
        assertThat(partner.id()).startsWith("ppap-access-bpn-BPNL1-");
        assertThat(partner.constraints())
                .extracting(PolicySpec.Constraint::leftOperand)
                .containsExactly("Membership", "BusinessPartnerNumber");
    }

    @Test
    void fallbackMirrorsTheConfiguration() {
        PolicyProfileInfo fallback = OfferPolicies.fallback(
                new EdcProperties.Policy("cx.quality.base:1", "DataExchangeGovernance:1.0", "ppap-partners"));
        assertThat(fallback.code()).isEqualTo("default");
        assertThat(fallback.membershipRequired()).isFalse();
        assertThat(fallback.usagePurposes()).containsExactly("cx.quality.base:1");
        assertThat(OfferPolicies.usage(fallback).id()).startsWith("ppap-usage-default-");
    }
}
