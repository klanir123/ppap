package com.msg.ppap.masterdata.domain;

import static com.msg.ppap.masterdata.domain.MasterdataRules.NAME_MAX;
import static com.msg.ppap.masterdata.domain.MasterdataRules.SHORT_MAX;

import com.msg.ppap.platform.domain.Validation;

/**
 * Validated attributes of a {@link Part}.
 *
 * @param partNumber own part number (business key)
 * @param nameDe German name
 * @param nameEn English name
 * @param drawingNumber drawing number
 * @param drawingRevision drawing revision index
 * @param material material
 * @param catenaXId Catena-X id ({@code urn:uuid:…}) for digital twin reuse
 */
public record PartValues(
        PartNumber partNumber,
        String nameDe,
        String nameEn,
        PartNumber drawingNumber,
        String drawingRevision,
        String material,
        String catenaXId) {

    /** Parses and validates raw input. */
    public static PartValues parse(
            String partNumber,
            String nameDe,
            String nameEn,
            String drawingNumber,
            String drawingRevision,
            String material,
            String catenaXId) {
        var v = new Validation();
        var values = new PartValues(
                v.required("partNumber", partNumber, PartNumber::new, "1-64 characters of [A-Za-z0-9._- /]"),
                v.required("nameDe", nameDe, NAME_MAX),
                v.optional("nameEn", nameEn, NAME_MAX),
                v.optional("drawingNumber", drawingNumber, PartNumber::new, "1-64 characters of [A-Za-z0-9._- /]"),
                v.optional("drawingRevision", drawingRevision, SHORT_MAX),
                v.optional("material", material, NAME_MAX),
                v.optionalMatching("catenaXId", catenaXId, SHORT_MAX, MasterdataRules.CATENA_X_ID, "urn:uuid:<uuid>"));
        v.throwIfInvalid();
        return values;
    }
}
