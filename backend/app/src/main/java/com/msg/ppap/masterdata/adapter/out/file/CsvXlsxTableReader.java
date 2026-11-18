package com.msg.ppap.masterdata.adapter.out.file;

import com.msg.ppap.masterdata.application.port.out.Table;
import com.msg.ppap.masterdata.application.port.out.TabularFileReader;
import com.msg.ppap.platform.domain.PpapException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.ss.usermodel.Comment;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.util.XMLHelper;
import org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.springframework.stereotype.Component;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

/**
 * Reads CSV (UTF-8, optional BOM, delimiter {@code ;} or {@code ,} auto-detected) and XLSX files (streaming SAX
 * reader, protected against zip bombs by POI's {@code ZipSecureFile} limits).
 */
@Component
class CsvXlsxTableReader implements TabularFileReader {

    static final String UNSUPPORTED = "UNSUPPORTED_FILE_TYPE";
    static final String EMPTY = "EMPTY_FILE";
    static final String TOO_MANY_ROWS = "TOO_MANY_ROWS";
    static final String UNREADABLE = "UNREADABLE_FILE";

    private static final byte[] ZIP_MAGIC = {0x50, 0x4B, 0x03, 0x04};

    static {
        PoiSecurity.apply();
    }

    @Override
    public Table read(String fileName, byte[] content, List<String> preferredSheets, int maxRows) {
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        boolean zip = startsWith(content, ZIP_MAGIC);
        if (name.endsWith(".xlsx") || (zip && !name.endsWith(".csv"))) {
            if (!zip) {
                throw new PpapException(PpapException.Kind.UNSUPPORTED, UNSUPPORTED, "Not a valid XLSX file");
            }
            return readXlsx(content, preferredSheets, maxRows);
        }
        if (name.endsWith(".csv") || name.endsWith(".txt") || name.isEmpty()) {
            return readCsv(content, maxRows);
        }
        throw new PpapException(PpapException.Kind.UNSUPPORTED, UNSUPPORTED, "Only CSV and XLSX files are supported");
    }

    // ---------------------------------------------------------------- CSV

    Table readCsv(byte[] content, int maxRows) {
        String text = decodeUtf8(content);
        if (!text.isEmpty() && text.charAt(0) == '﻿') {
            text = text.substring(1);
        }
        char delimiter = detectDelimiter(text);
        CSVFormat format = CSVFormat.DEFAULT
                .builder()
                .setDelimiter(delimiter)
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .get();
        List<String> columns = null;
        List<Table.Row> rows = new ArrayList<>();
        try (CSVParser parser = CSVParser.parse(new StringReader(text), format)) {
            for (CSVRecord record : parser) {
                List<String> cells = record.toList();
                if (cells.stream().allMatch(String::isBlank)) {
                    continue;
                }
                if (columns == null) {
                    columns = cells;
                    continue;
                }
                if (rows.size() >= maxRows) {
                    throw tooManyRows(maxRows);
                }
                rows.add(new Table.Row((int) record.getRecordNumber(), cells));
            }
        } catch (IOException | IllegalStateException | java.io.UncheckedIOException ex) {
            throw new PpapException(PpapException.Kind.INVALID, UNREADABLE, "CSV file could not be parsed");
        }
        if (columns == null) {
            throw new PpapException(PpapException.Kind.INVALID, EMPTY, "File has no header row");
        }
        return new Table(null, String.valueOf(delimiter), columns, rows);
    }

    /** {@code ;} or {@code ,}, whichever occurs more often outside quotes in the header line. */
    static char detectDelimiter(String text) {
        int semicolons = 0;
        int commas = 0;
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"') {
                quoted = !quoted;
            } else if (!quoted && (c == '\n' || c == '\r')) {
                if (semicolons + commas > 0) {
                    break;
                }
            } else if (!quoted && c == ';') {
                semicolons++;
            } else if (!quoted && c == ',') {
                commas++;
            }
        }
        return commas > semicolons ? ',' : ';';
    }

    private static String decodeUtf8(byte[] content) {
        try {
            return StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(content))
                    .toString();
        } catch (CharacterCodingException ex) {
            throw new PpapException(PpapException.Kind.INVALID, UNREADABLE, "CSV file must be UTF-8 encoded");
        }
    }

    // ---------------------------------------------------------------- XLSX

    Table readXlsx(byte[] content, List<String> preferredSheets, int maxRows) {
        try (OPCPackage pkg = OPCPackage.open(new ByteArrayInputStream(content))) {
            XSSFReader reader = new XSSFReader(pkg);
            String sheetName = null;
            byte[] sheetXml = null;
            XSSFReader.SheetIterator sheets = (XSSFReader.SheetIterator) reader.getSheetsData();
            while (sheets.hasNext()) {
                try (InputStream stream = sheets.next()) {
                    String current = sheets.getSheetName();
                    boolean preferred = preferredSheets.stream().anyMatch(current::equalsIgnoreCase);
                    if (sheetXml == null || preferred) {
                        sheetName = current;
                        sheetXml = stream.readAllBytes();
                        if (preferred) {
                            break;
                        }
                    }
                }
            }
            if (sheetXml == null) {
                throw new PpapException(PpapException.Kind.INVALID, EMPTY, "Workbook has no sheets");
            }
            var collector = new RowCollector(maxRows);
            var handler = new XSSFSheetXMLHandler(
                    reader.getStylesTable(),
                    null,
                    new ReadOnlySharedStringsTable(pkg),
                    collector,
                    new DataFormatter(Locale.ROOT),
                    false);
            XMLReader parser = XMLHelper.newXMLReader();
            parser.setContentHandler(handler);
            parser.parse(new InputSource(new ByteArrayInputStream(sheetXml)));
            if (collector.columns == null) {
                throw new PpapException(PpapException.Kind.INVALID, EMPTY, "Sheet has no header row");
            }
            return new Table(sheetName, null, collector.columns, collector.rows);
        } catch (PpapException ex) {
            throw ex;
        } catch (RowLimitExceeded ex) {
            throw tooManyRows(maxRows);
        } catch (Exception ex) {
            throw new PpapException(PpapException.Kind.INVALID, UNREADABLE, "XLSX file could not be read");
        }
    }

    private static PpapException tooManyRows(int maxRows) {
        return new PpapException(
                PpapException.Kind.INVALID, TOO_MANY_ROWS, "File exceeds the limit of " + maxRows + " rows");
    }

    private static boolean startsWith(byte[] content, byte[] prefix) {
        if (content.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (content[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    /** Signals that the row limit was exceeded while streaming. */
    private static final class RowLimitExceeded extends RuntimeException {
        RowLimitExceeded() {
            super(null, null, false, false);
        }
    }

    /** Collects the first row as header and all further non-empty rows aligned to the header. */
    private static final class RowCollector implements XSSFSheetXMLHandler.SheetContentsHandler {

        private final int maxRows;
        private List<String> columns;
        private final List<Table.Row> rows = new ArrayList<>();
        private List<String> current;

        RowCollector(int maxRows) {
            this.maxRows = maxRows;
        }

        @Override
        public void startRow(int rowNum) {
            current = new ArrayList<>();
        }

        @Override
        public void endRow(int rowNum) {
            if (current.stream().allMatch(String::isBlank)) {
                return;
            }
            if (columns == null) {
                while (!current.isEmpty() && current.getLast().isBlank()) {
                    current.removeLast();
                }
                columns = List.copyOf(current);
                return;
            }
            if (rows.size() >= maxRows) {
                throw new RowLimitExceeded();
            }
            while (current.size() < columns.size()) {
                current.add("");
            }
            rows.add(new Table.Row(rowNum + 1, current.subList(0, Math.max(columns.size(), 0))));
        }

        @Override
        public void cell(String cellReference, String formattedValue, Comment comment) {
            int column = cellReference == null ? current.size() : new CellReference(cellReference).getCol();
            while (current.size() < column) {
                current.add("");
            }
            current.add(formattedValue == null ? "" : formattedValue.strip());
        }

        @Override
        public void headerFooter(String text, boolean isHeader, String tagName) {}
    }
}
