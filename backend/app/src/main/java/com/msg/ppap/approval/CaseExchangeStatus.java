package com.msg.ppap.approval;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Extension point: delivery state of the latest outbound partner message per case (delivery badge in lists and
 * detail). Implemented by the {@code exchange} module; approval only knows this interface (no dependency cycle).
 */
public interface CaseExchangeStatus {

    /** Latest outbound message per case; cases without outbound messages are missing in the result. */
    Map<UUID, LastOutbound> lastOutbound(Collection<UUID> caseIds);

    /**
     * Latest outbound message of a case.
     *
     * @param messageType e.g. {@code PPAP_SUBMISSION}
     * @param status {@code PENDING}, {@code IN_PROGRESS}, {@code DELIVERED} or {@code FAILED}
     * @param at time of the last change (delivery or creation)
     */
    record LastOutbound(String messageType, String status, Instant at) {}
}
