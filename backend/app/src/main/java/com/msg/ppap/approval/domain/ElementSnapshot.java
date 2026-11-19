package com.msg.ppap.approval.domain;

import java.util.Map;
import java.util.Objects;

/**
 * Element definition frozen at request time (UC-02: catalogue changes do not affect running cases).
 *
 * @param valueSchema JSON Schema (draft 2020-12) of the structured values, or {@code null}
 */
public record ElementSnapshot(
        String nameDe, String nameEn, EvidenceType evidenceType, Map<String, Object> valueSchema) {

    public ElementSnapshot {
        Objects.requireNonNull(evidenceType, "evidenceType");
        nameDe = nameDe == null ? "" : nameDe;
        nameEn = nameEn == null ? nameDe : nameEn;
        valueSchema = valueSchema == null || valueSchema.isEmpty() ? null : valueSchema;
    }
}
