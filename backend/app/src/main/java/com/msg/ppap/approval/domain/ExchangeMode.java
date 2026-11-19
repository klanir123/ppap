package com.msg.ppap.approval.domain;

/**
 * How a submitted revision is provided to / was received from the partner (docs/api/pull-api.md, ADR-0002). The
 * supplier decides per relation; the effective mode is recorded per revision on both sides.
 */
public enum ExchangeMode {
    /** Complete payload in the {@code PPAP_SUBMISSION} notification (phase 4 behaviour). */
    PUSH,
    /** Digital twin + {@code PPAP_SUBMISSION_AVAILABLE} notification, the customer pulls the payload. */
    PUSH_NOTIFY_PULL_PAYLOAD,
    /** Digital twin only; the customer polls the supplier's DTR. */
    PULL;

    /** Mode of a relation value; unknown or missing values fall back to the default. */
    public static ExchangeMode of(String value) {
        if (value == null || value.isBlank()) {
            return PUSH_NOTIFY_PULL_PAYLOAD;
        }
        try {
            return valueOf(value);
        } catch (IllegalArgumentException ex) {
            return PUSH_NOTIFY_PULL_PAYLOAD;
        }
    }

    /** Whether the revision is registered as digital twin (pull possible). */
    public boolean registersTwin() {
        return this != PUSH;
    }
}
