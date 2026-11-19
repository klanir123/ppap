package com.msg.ppap.semantics.api;

/** Model URNs of the exchange format (SAMM namespace {@code urn:samm:com.msg.ppap:0.1.0#}). */
public final class ExchangeModels {

    /** Current exchange format version. */
    public static final String VERSION = "0.1.0";

    public static final String PPAP_REQUEST = "urn:samm:com.msg.ppap:0.1.0#PpapRequest";
    public static final String PPAP_REQUEST_WITHDRAWN = "urn:samm:com.msg.ppap:0.1.0#PpapRequestWithdrawn";
    public static final String PPAP_STATUS_UPDATE = "urn:samm:com.msg.ppap:0.1.0#PpapStatusUpdate";
    public static final String PPAP_SUBMISSION = "urn:samm:com.msg.ppap:0.1.0#PpapSubmission";
    public static final String PPAP_DECISION = "urn:samm:com.msg.ppap:0.1.0#PpapDecision";

    /** Envelope of all messages (header = Catena-X message header 3.0.0). */
    public static final String PPAP_NOTIFICATION = "urn:samm:com.msg.ppap:0.1.0#PpapNotification";

    private ExchangeModels() {}
}
