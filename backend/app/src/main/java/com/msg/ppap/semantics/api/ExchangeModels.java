package com.msg.ppap.semantics.api;

/** Model URNs of the exchange format (SAMM namespace {@code urn:samm:com.msg.ppap:0.1.0#}). */
public final class ExchangeModels {

    /** Current exchange format version. */
    public static final String VERSION = "0.1.0";

    public static final String PPAP_REQUEST = "urn:samm:com.msg.ppap:0.1.0#PpapRequest";
    public static final String PPAP_SUBMISSION = "urn:samm:com.msg.ppap:0.1.0#PpapSubmission";
    public static final String PPAP_DECISION = "urn:samm:com.msg.ppap:0.1.0#PpapDecision";

    private ExchangeModels() {}
}
