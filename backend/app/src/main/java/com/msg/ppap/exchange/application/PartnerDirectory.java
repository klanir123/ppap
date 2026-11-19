package com.msg.ppap.exchange.application;

import com.msg.ppap.exchange.application.port.out.PartnerEndpoint;
import com.msg.ppap.exchange.domain.StepName;
import com.msg.ppap.masterdata.api.MasterdataQueries;
import com.msg.ppap.masterdata.api.MasterdataQueries.PartnerInfo;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Partner connector coordinates (DSP endpoint, DID) and relation checks from master data. */
@Component
public class PartnerDirectory {

    private final MasterdataQueries masterdata;

    public PartnerDirectory(MasterdataQueries masterdata) {
        this.masterdata = masterdata;
    }

    /**
     * Connector coordinates of an exchange-ready partner.
     *
     * @throws DataspaceException (retryable) if the partner is unknown, inactive or lacks DSP endpoint/DID
     */
    public PartnerEndpoint endpoint(String bpnl, StepName step) {
        PartnerInfo partner = masterdata
                .findPartner(bpnl)
                .filter(PartnerInfo::exchangeReady)
                .orElseThrow(() -> new PartnerNotReadyException(step, bpnl));
        return new PartnerEndpoint(partner.bpnl(), partner.did(), partner.dspEndpoint());
    }

    /** Whether the partner is active and has at least one active relation. */
    public boolean hasActiveRelation(String bpnl) {
        boolean activePartner =
                masterdata.findPartner(bpnl).map(PartnerInfo::active).orElse(false);
        return activePartner && (relationActive(bpnl, "CUSTOMER") || relationActive(bpnl, "SUPPLIER"));
    }

    public Optional<String> name(String bpnl) {
        return masterdata.findPartner(bpnl).map(PartnerInfo::name);
    }

    private boolean relationActive(String bpnl, String role) {
        return masterdata
                .findRelation(bpnl, role)
                .map(MasterdataQueries.RelationInfo::active)
                .orElse(false);
    }
}
