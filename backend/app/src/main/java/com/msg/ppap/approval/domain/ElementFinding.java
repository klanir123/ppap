package com.msg.ppap.approval.domain;

import java.util.Objects;

/** Review finding of the customer on one element. */
public record ElementFinding(String code, FindingResult result, String comment) {

    public ElementFinding {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(result, "result");
        comment = comment == null || comment.isBlank() ? null : comment;
    }
}
