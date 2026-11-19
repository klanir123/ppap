package com.msg.ppap.approval.domain;

import java.util.Objects;

/** Element requested by the customer. */
public record RequestedElement(String code, Requirement requirement, String note, ElementSnapshot snapshot) {

    public RequestedElement {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(requirement, "requirement");
        Objects.requireNonNull(snapshot, "snapshot");
        note = note == null || note.isBlank() ? null : note;
    }
}
