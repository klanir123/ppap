package com.msg.ppap.exchange.application;

import com.msg.ppap.approval.PartnerKeyRefresh;
import com.msg.ppap.exchange.application.port.out.DataspaceException;
import com.msg.ppap.exchange.application.port.out.StepListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** {@link PartnerKeyRefresh}: fetches the partner's JWKS via EDC before a manual signature verification. */
@Service
public class PartnerKeyRefreshService implements PartnerKeyRefresh {

    private static final Logger LOG = LoggerFactory.getLogger(PartnerKeyRefreshService.class);

    private final PartnerKeyService keys;

    public PartnerKeyRefreshService(PartnerKeyService keys) {
        this.keys = keys;
    }

    @Override
    public boolean refresh(String partnerBpnl) {
        try {
            return keys.fetch(partnerBpnl, StepListener.NONE) > 0;
        } catch (DataspaceException ex) {
            LOG.info("Keys of {} could not be fetched: {}", partnerBpnl, ex.getMessage());
            return false;
        }
    }
}
