package com.msg.ppap.masterdata.application;

import com.msg.ppap.masterdata.domain.PartValues;

/**
 * Raw input for creating or updating a part (from REST or import).
 *
 * @param active desired active flag; {@code null} keeps the current state (new objects are active)
 */
public record PartCommand(
        String partNumber,
        String nameDe,
        String nameEn,
        String drawingNumber,
        String drawingRevision,
        String material,
        String catenaXId,
        Boolean active) {

    PartValues toValues() {
        return PartValues.parse(partNumber, nameDe, nameEn, drawingNumber, drawingRevision, material, catenaXId);
    }
}
