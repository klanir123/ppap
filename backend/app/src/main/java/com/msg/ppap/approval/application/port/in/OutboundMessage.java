package com.msg.ppap.approval.application.port.in;

import com.msg.ppap.semantics.api.EncodedPayload;
import com.msg.ppap.semantics.api.Notification.MessageType;
import java.util.Objects;
import java.util.UUID;

/**
 * A message to be delivered to the partner of a case, created in the same transaction as the case change.
 *
 * @param messageType message type of the envelope
 * @param caseId own case
 * @param caseNumber own case number
 * @param exchangeId exchange id shared with the partner's case
 * @param partnerBpnl receiver
 * @param payload validated canonical payload
 * @param signature detached JWS over the payload ({@code null} for unsigned message types)
 */
public record OutboundMessage(
        MessageType messageType,
        UUID caseId,
        String caseNumber,
        UUID exchangeId,
        String partnerBpnl,
        EncodedPayload payload,
        String signature) {

    public OutboundMessage {
        Objects.requireNonNull(messageType, "messageType");
        Objects.requireNonNull(caseId, "caseId");
        Objects.requireNonNull(exchangeId, "exchangeId");
        Objects.requireNonNull(partnerBpnl, "partnerBpnl");
        Objects.requireNonNull(payload, "payload");
    }
}
