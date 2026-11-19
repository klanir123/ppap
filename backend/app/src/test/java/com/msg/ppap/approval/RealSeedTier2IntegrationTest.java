package com.msg.ppap.approval;

import org.springframework.test.context.TestPropertySource;

/** Real seed of participant tier2. */
@TestPropertySource(properties = "ppap.tenant.bpnl=BPNL000000TIER02")
class RealSeedTier2IntegrationTest extends AbstractRealSeedIntegrationTest {

    @Override
    String participant() {
        return "tier2";
    }

    @Override
    String tenant() {
        return "BPNL000000TIER02";
    }

    @Override
    int minimumCases() {
        return 3;
    }
}
