package com.msg.ppap.exchange.domain;

/** Single steps of an exchange (docs/api/exchange-api.md, docs/api/pull-api.md). */
public enum StepName {
    CATALOG,
    NEGOTIATION,
    TRANSFER,
    EDR,
    DELIVERY,
    RECEIPT,
    VALIDATION,
    PROCESSING,
    KEY_FETCH,
    /** Phase 5: lookup of the shell in the partner's DTR ({@code GET /lookup/shells}). */
    DTR_LOOKUP,
    /** Phase 5: shell descriptor read from the partner's DTR. */
    SHELL_DESCRIPTOR,
    /** Phase 5: submodel {@code PpapSubmission} fetched via the partner's data plane. */
    SUBMODEL_FETCH,
    /** Phase 5: SHA-256 of pulled content compared with the announced hash. */
    HASH_CHECK,
    /** Phase 5: JWS of a pulled submission verified against the partner's keys. */
    SIGNATURE_CHECK,
    /** Phase 5: document fetched via {@code ppap-documents-<BPNL>} of the partner. */
    DOCUMENT_FETCH,
    /** Phase 5: shell / submodel descriptors registered in the own DTR. */
    TWIN_REGISTRATION
}
