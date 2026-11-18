package com.msg.ppap.masterdata.application.imports;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Importable master data entities with their target fields (= seed file columns). */
public enum ImportEntity {
    PARTS(
            "parts",
            "parts",
            List.of(
                    TargetField.required("partNumber"),
                    TargetField.required("nameDe"),
                    TargetField.optional("nameEn"),
                    TargetField.optional("drawingNumber"),
                    TargetField.optional("drawingRevision"),
                    TargetField.optional("material"),
                    TargetField.optional("catenaXId")),
            List.of("partNumber")),
    PARTNERS(
            "partners",
            "partners",
            List.of(
                    TargetField.required("bpnl"),
                    TargetField.required("name"),
                    TargetField.required("roles"),
                    TargetField.optional("dspEndpoint"),
                    TargetField.optional("did"),
                    TargetField.optional("contactEmail")),
            List.of("bpnl")),
    SITES(
            "sites",
            "sites",
            List.of(
                    TargetField.required("bpns"),
                    TargetField.required("name"),
                    TargetField.required("ownerBpnl"),
                    TargetField.optional("street"),
                    TargetField.optional("postalCode"),
                    TargetField.optional("city"),
                    TargetField.optional("country")),
            List.of("bpns")),
    RELATIONS(
            "relations",
            "relations",
            List.of(
                    TargetField.required("partnerBpnl"),
                    TargetField.required("partnerRole"),
                    TargetField.optional("exchangeMode")),
            List.of("partnerBpnl", "partnerRole")),
    PART_MAPPINGS(
            "part-mappings",
            "partMappings",
            List.of(
                    TargetField.required("partnerBpnl"),
                    TargetField.required("partnerRole"),
                    TargetField.required("partNumber"),
                    TargetField.required("partnerPartNumber"),
                    TargetField.optional("ownSiteBpns"),
                    TargetField.optional("partnerSiteBpns")),
            List.of("partnerBpnl", "partnerRole", "partNumber"));

    private final String pathValue;
    private final String labelGroup;
    private final List<TargetField> fields;
    private final List<String> keyFields;

    ImportEntity(String pathValue, String labelGroup, List<TargetField> fields, List<String> keyFields) {
        this.pathValue = pathValue;
        this.labelGroup = labelGroup;
        this.fields = fields;
        this.keyFields = keyFields;
    }

    /** Value used in paths and reports, e.g. {@code part-mappings}. */
    public String pathValue() {
        return pathValue;
    }

    /** i18n key of a field label, e.g. {@code masterdata.parts.partNumber}. */
    public String labelKey(String field) {
        return "masterdata." + labelGroup + "." + field;
    }

    public List<TargetField> fields() {
        return fields;
    }

    /** Fields forming the business key of a row. */
    public List<String> keyFields() {
        return keyFields;
    }

    /** Whether the entity has the given target field. */
    public boolean hasField(String name) {
        return fields.stream().anyMatch(field -> field.name().equals(name));
    }

    /** Sheet names preferred in workbooks. */
    public List<String> sheetNames() {
        return List.of(pathValue, labelGroup, name());
    }

    /** Resolves a path value (case-insensitive). */
    public static Optional<ImportEntity> fromPath(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(entity -> entity.pathValue.equals(normalized))
                .findFirst();
    }

    /**
     * Target field of an import.
     *
     * @param name field name
     * @param required whether a value is mandatory
     */
    public record TargetField(String name, boolean required) {

        static TargetField required(String name) {
            return new TargetField(name, true);
        }

        static TargetField optional(String name) {
            return new TargetField(name, false);
        }
    }
}
