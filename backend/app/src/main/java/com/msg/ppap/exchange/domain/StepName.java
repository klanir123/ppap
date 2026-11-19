package com.msg.ppap.exchange.domain;

/** Single steps of an exchange (docs/api/exchange-api.md). */
public enum StepName {
    CATALOG,
    NEGOTIATION,
    TRANSFER,
    EDR,
    DELIVERY,
    RECEIPT,
    VALIDATION,
    PROCESSING,
    KEY_FETCH
}
