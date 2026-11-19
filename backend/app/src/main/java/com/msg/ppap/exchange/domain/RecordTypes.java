package com.msg.ppap.exchange.domain;

/**
 * Record kinds of {@code ex_message} that are not notifications (docs/api/pull-api.md, „Neue Schritt-Typen“): local
 * twin registration in the outbox and pull jobs of the own participant (worked off like the outbox, with retry).
 */
public final class RecordTypes {

    /** Outbound, local: register the shell / submodel descriptors in the own DTR before the notification. */
    public static final String TWIN_REGISTRATION = "TWIN_REGISTRATION";

    /** Inbound pull: submitted revision via the partner's DTR and submodel endpoint. */
    public static final String PULL_SUBMISSION = "PULL_SUBMISSION";

    /** Inbound pull: document of the partner via {@code ppap-documents-<own BPNL>}. */
    public static final String PULL_DOCUMENT = "PULL_DOCUMENT";

    private RecordTypes() {}

    public static boolean isPull(String messageType) {
        return PULL_SUBMISSION.equals(messageType) || PULL_DOCUMENT.equals(messageType);
    }

    /** Whether an outbound record is executed locally instead of being delivered via EDC. */
    public static boolean isLocal(String messageType) {
        return TWIN_REGISTRATION.equals(messageType);
    }
}
