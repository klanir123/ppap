package com.msg.ppap.approval;

import org.springframework.test.context.TestPropertySource;

/** Real seed of participant oem. */
@TestPropertySource(properties = "ppap.tenant.bpnl=BPNL00000000OEM1")
class RealSeedOemIntegrationTest extends AbstractRealSeedIntegrationTest {

    @Override
    String participant() {
        return "oem";
    }

    @Override
    String tenant() {
        return "BPNL00000000OEM1";
    }

    @Override
    int minimumCases() {
        return 9;
    }
}
