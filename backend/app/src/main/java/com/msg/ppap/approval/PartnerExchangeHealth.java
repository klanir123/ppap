package com.msg.ppap.approval;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;

/**
 * Extension point: health of the dataspace exchange with partners (dashboard, docs/api/cascade-transparency-api.md
 * §4). Implemented by the {@code exchange} module; approval only knows this interface (no dependency cycle).
 */
public interface PartnerExchangeHealth {

    /** Exchange health per partner BPNL; partners without any message are missing in the result. */
    Map<String, Health> health(Collection<String> partnerBpnls);

    /**
     * Health of the exchange with one partner.
     *
     * @param status {@code OK}, {@code DEGRADED} (retries or long pending messages) or {@code FAILING} (failed or
     *     rejected messages) within the last 24 hours
     * @param lastExchangeAt time of the last exchanged message
     */
    record Health(String status, Instant lastExchangeAt) {}
}
