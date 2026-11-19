package com.msg.ppap.exchange.application;

import com.msg.ppap.exchange.application.port.out.DataspaceException;
import com.msg.ppap.exchange.domain.StepName;

/** The partner cannot be addressed via the dataspace (master data incomplete); retryable after a correction. */
public class PartnerNotReadyException extends DataspaceException {

    public PartnerNotReadyException(StepName step, String bpnl) {
        super(step, "Partner " + bpnl + " is not exchange ready (inactive, no DSP endpoint or DID)");
    }
}
