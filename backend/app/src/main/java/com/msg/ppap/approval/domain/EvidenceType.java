package com.msg.ppap.approval.domain;

/** Kind of evidence an element expects (snapshot of the catalogue). */
public enum EvidenceType {
    DOCUMENT,
    VALUES,
    DOCUMENT_AND_VALUES;

    public boolean needsDocument() {
        return this != VALUES;
    }

    public boolean needsValues() {
        return this != DOCUMENT;
    }
}
