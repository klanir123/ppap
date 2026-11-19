package com.msg.ppap.approval.application.port.in;

import com.msg.ppap.approval.ApprovalEvent;
import java.util.Optional;

/**
 * Decides which partner message a domain event of the approval module triggers (docs/api/exchange-api.md, „Ereignis
 * → Nachricht → Wirkung“) and builds its payload: requests and status updates are encoded now, submissions and
 * decisions are the stored canonical payloads with their JWS. Must be called within the transaction of the change.
 */
public interface OutboundMessageUseCase {

    /**
     * Message for the event.
     *
     * @return empty if the event is not communicated to the partner (local-only cases, inbound transitions, edits)
     */
    Optional<OutboundMessage> messageFor(ApprovalEvent event);
}
