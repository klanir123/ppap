package com.msg.ppap.masterdata.domain;

/** How PPAP submissions are exchanged with the partner (ADR-0002). */
public enum ExchangeMode {
    PUSH,
    PULL,
    PUSH_NOTIFY_PULL_PAYLOAD
}
