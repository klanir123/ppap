package com.msg.ppap.approval.application.port.in;

import java.util.Objects;

/**
 * A message received from a partner (phase 4: via EDC data plane).
 *
 * @param senderBpnl BPNL of the sending participant (from the verified dataspace context)
 * @param payload payload bytes as received
 * @param jws detached JWS over the payload; {@code null} for unsigned message types (requests)
 * @param exchangeMode how a submission was received ({@code PUSH}, {@code PUSH_NOTIFY_PULL_PAYLOAD}, {@code PULL});
 *     {@code null} = PUSH (notification)
 */
public record InboundMessage(String senderBpnl, byte[] payload, String jws, String exchangeMode) {

    public InboundMessage {
        Objects.requireNonNull(senderBpnl, "senderBpnl");
        Objects.requireNonNull(payload, "payload");
        payload = payload.clone();
    }

    /** Message received as notification (push). */
    public InboundMessage(String senderBpnl, byte[] payload, String jws) {
        this(senderBpnl, payload, jws, null);
    }

    @Override
    public byte[] payload() {
        return payload.clone();
    }
}
