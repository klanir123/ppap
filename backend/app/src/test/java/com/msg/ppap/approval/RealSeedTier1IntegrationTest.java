package com.msg.ppap.approval;

import org.springframework.test.context.TestPropertySource;

/** Real seed of participant tier1. */
@TestPropertySource(properties = "ppap.tenant.bpnl=BPNL000000TIER01")
class RealSeedTier1IntegrationTest extends AbstractRealSeedIntegrationTest {

    @Override
    String participant() {
        return "tier1";
    }

    @Override
    String tenant() {
        return "BPNL000000TIER01";
    }

    @Override
    int minimumCases() {
        return 10;
    }
}
