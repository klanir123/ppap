package com.msg.ppap.approval.domain;

import java.util.Objects;

/** Partner of the case (snapshot of name). */
public record PartnerRef(String bpnl, String name) {

    public PartnerRef {
        Objects.requireNonNull(bpnl, "bpnl");
        name = name == null ? bpnl : name;
    }
}
