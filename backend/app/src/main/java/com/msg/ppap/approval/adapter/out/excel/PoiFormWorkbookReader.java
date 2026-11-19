package com.msg.ppap.approval.adapter.out.excel;

import com.msg.ppap.approval.application.port.out.FormWorkbookReader;
import com.msg.ppap.platform.domain.PpapException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

/**
 * Reads PPF forms with Apache POI. Forms are small (one sheet, a few dozen rows), so the workbook is opened in memory
 * after the size check of the caller; zip bomb protection uses the same {@code ZipSecureFile} limits as the master
 * data import. Formulas are not evaluated (cached results are used).
 */
@Component
class PoiFormWorkbookReader implements FormWorkbookReader {

    static final String UNREADABLE_FILE = "UNREADABLE_FILE";

    PoiFormWorkbookReader() {
        ZipSecureFile.setMinInflateRatio(0.01);
        ZipSecureFile.setMaxEntrySize(100L * 1024 * 1024);
        ZipSecureFile.setMaxTextSize(50L * 1024 * 1024);
    }

    @Override
    public Workbook open(byte[] content) {
        try {
            var workbook = new XSSFWorkbook(new ByteArrayInputStream(content));
            return new PoiWorkbook(workbook);
        } catch (IOException | RuntimeException ex) {
            throw new PpapException(
                    PpapException.Kind.UNPROCESSABLE, UNREADABLE_FILE, "File is not a readable XLSX workbook");
        }
    }

    private static final class PoiWorkbook implements Workbook {

        private final XSSFWorkbook workbook;
        private final DataFormatter formatter = new DataFormatter();

        PoiWorkbook(XSSFWorkbook workbook) {
            this.workbook = workbook;
        }

        @Override
        public String cell(String sheetName, String reference) {
            Sheet sheet = workbook.getSheet(sheetName);
            if (sheet == null || reference == null) {
                return null;
            }
            var ref = new CellReference(reference);
            Row row = sheet.getRow(ref.getRow());
            if (row == null) {
                return null;
            }
            Cell cell = row.getCell(ref.getCol());
            if (cell == null) {
                return null;
            }
            String value = formatter.formatCellValue(cell).strip();
            return value.isEmpty() ? null : value;
        }

        @Override
        public int lastRow(String sheetName) {
            Sheet sheet = workbook.getSheet(sheetName);
            return sheet == null ? 0 : sheet.getLastRowNum() + 1;
        }

        @Override
        public void close() {
            try {
                workbook.close();
            } catch (IOException ignored) {
                // read-only workbook from memory
            }
        }
    }
}
