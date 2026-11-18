package com.msg.ppap.masterdata.application.port.out;

import java.util.List;

/**
 * Content of an import file.
 *
 * @param sheet sheet name (XLSX) or {@code null}
 * @param delimiter detected delimiter (CSV) or {@code null}
 * @param columns header cells in file order
 * @param rows data rows, each with the source row number (header = 1) and cells aligned with {@code columns}
 */
public record Table(String sheet, String delimiter, List<String> columns, List<Row> rows) {

    public Table {
        columns = List.copyOf(columns);
        rows = List.copyOf(rows);
    }

    /**
     * A data row.
     *
     * @param rowNumber 1-based row number in the file
     * @param cells cell values aligned with the columns (missing cells are empty strings)
     */
    public record Row(int rowNumber, List<String> cells) {

        public Row {
            cells = List.copyOf(cells);
        }
    }
}
