package com.msg.ppap.exchange.application.port.out;

import java.util.Objects;

/**
 * Connector coordinates of a partner (from master data).
 *
 * @param bpnl business partner number
 * @param did participant id (DID) of the partner's connector
 * @param dspEndpoint DSP endpoint of the partner's control plane
 */
public record PartnerEndpoint(String bpnl, String did, String dspEndpoint) {

    public PartnerEndpoint {
        Objects.requireNonNull(bpnl, "bpnl");
        Objects.requireNonNull(did, "did");
        Objects.requireNonNull(dspEndpoint, "dspEndpoint");
    }
}
