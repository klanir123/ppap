package com.msg.ppap.masterdata.domain;

/** Master data object types (audit object types). */
public enum MasterdataType {
    PART("Part"),
    PARTNER("Partner"),
    SITE("Site"),
    RELATION("Relation"),
    PART_MAPPING("Part mapping"),
    CATALOG_ELEMENT("Catalog element"),
    SUBMISSION_LEVEL("Submission level"),
    SUBMISSION_REASON("Submission reason");

    private final String label;

    MasterdataType(String label) {
        this.label = label;
    }

    /** Human-readable English label. */
    public String label() {
        return label;
    }
}
