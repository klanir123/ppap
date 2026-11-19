package com.msg.ppap.approval.domain;

import java.util.List;
import java.util.Map;

/** Validates structured element values against the element's JSON Schema (implemented by an adapter). */
public interface ValueValidator {

    /**
     * Validates values.
     *
     * @param schema JSON Schema (draft 2020-12)
     * @param values JSON value tree
     * @return violations with RFC 6901 JSON pointers into {@code values}, empty if valid
     */
    List<Violation> validate(Map<String, Object> schema, Object values);

    /** A schema violation. */
    record Violation(String pointer, String message) {}
}
