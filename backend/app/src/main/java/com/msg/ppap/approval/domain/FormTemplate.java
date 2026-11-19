package com.msg.ppap.approval.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Definition of an Excel PPF form (data, not code; seed {@code form-templates.json}): marker cell for recognition,
 * header cells, element table and mapping of self assessment texts.
 *
 * @param selfAssessmentValues form text (e.g. {@code i.O.}) → {@link SelfAssessment} name
 */
public record FormTemplate(
        String code,
        String nameDe,
        String nameEn,
        Marker marker,
        List<HeaderField> header,
        ElementTable elementTable,
        Map<String, String> selfAssessmentValues,
        boolean active) {

    public FormTemplate {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(marker, "marker");
        Objects.requireNonNull(elementTable, "elementTable");
        nameDe = nameDe == null ? code : nameDe;
        nameEn = nameEn == null ? nameDe : nameEn;
        header = header == null ? List.of() : List.copyOf(header);
        selfAssessmentValues = selfAssessmentValues == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(selfAssessmentValues));
    }

    /** Marker cell identifying the template. */
    public record Marker(String sheet, String cell, String value) {

        public Marker {
            Objects.requireNonNull(sheet, "sheet");
            Objects.requireNonNull(cell, "cell");
            Objects.requireNonNull(value, "value");
        }

        /** Whether the cell content matches (trimmed, case-insensitive). */
        public boolean matches(String content) {
            return content != null && content.strip().equalsIgnoreCase(value.strip());
        }
    }

    /** Header field read from a cell. */
    public record HeaderField(String field, String sheet, String cell) {}

    /**
     * Element table.
     *
     * @param lastRow last row (inclusive) or {@code null} for "until the last used row"
     * @param columns logical column ({@code code}, {@code selfAssessment}, {@code comment}) → column letter
     */
    public record ElementTable(String sheet, int firstRow, Integer lastRow, Map<String, String> columns) {

        public static final String CODE = "code";
        public static final String SELF_ASSESSMENT = "selfAssessment";
        public static final String COMMENT = "comment";

        public ElementTable {
            Objects.requireNonNull(sheet, "sheet");
            columns = columns == null ? Map.of() : Map.copyOf(columns);
            if (firstRow < 1 || (lastRow != null && lastRow < firstRow)) {
                throw new IllegalArgumentException("invalid element table rows");
            }
            if (!columns.containsKey(CODE)) {
                throw new IllegalArgumentException("element table needs a 'code' column");
            }
        }

        /** Cell reference of a logical column in a row, or {@code null} if the column is not mapped. */
        public String cell(String column, int row) {
            String letter = columns.get(column);
            return letter == null ? null : letter + row;
        }
    }

    /**
     * Maps a form text to a self assessment: exact match first, then trimmed and case-insensitive.
     *
     * @return empty for blank text
     * @throws IllegalArgumentException for unknown texts
     */
    public Optional<SelfAssessment> selfAssessment(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        String mapped = selfAssessmentValues.get(text);
        if (mapped == null) {
            String normalized = text.strip().toLowerCase(Locale.ROOT);
            mapped = selfAssessmentValues.entrySet().stream()
                    .filter(e -> e.getKey().strip().toLowerCase(Locale.ROOT).equals(normalized))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("unknown self assessment '" + text + "'"));
        }
        return Optional.of(SelfAssessment.valueOf(mapped.strip().toUpperCase(Locale.ROOT)));
    }
}
