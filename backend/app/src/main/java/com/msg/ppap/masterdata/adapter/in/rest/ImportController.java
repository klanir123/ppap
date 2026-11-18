package com.msg.ppap.masterdata.adapter.in.rest;

import com.msg.ppap.masterdata.application.imports.ImportEntity;
import com.msg.ppap.masterdata.application.imports.ImportFailedException;
import com.msg.ppap.masterdata.application.imports.ImportMessage;
import com.msg.ppap.masterdata.application.imports.ImportRow;
import com.msg.ppap.masterdata.application.imports.ImportService;
import com.msg.ppap.platform.domain.NotFoundException;
import com.msg.ppap.platform.domain.PpapException;
import com.msg.ppap.platform.domain.ValidationException;
import com.msg.ppap.platform.domain.ViolationCodes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** CSV/XLSX import of master data (UC-01 A1): preview, execute, error report. Stateless. */
@RestController
@RequestMapping("/api/v1/imports/{entity}")
@Tag(name = "imports")
@PreAuthorize(RestSupport.WRITE)
public class ImportController {

    static final String TEXT_CSV = "text/csv";
    private static final TypeReference<Map<String, String>> MAPPING_TYPE = new TypeReference<>() {};

    private final ImportService imports;
    private final JsonMapper jsonMapper;

    public ImportController(ImportService imports, JsonMapper jsonMapper) {
        this.imports = imports;
        this.jsonMapper = jsonMapper;
    }

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(operationId = "previewImport", summary = "Validate an import file and suggest a column mapping")
    public ImportPreviewResponse preview(
            @PathVariable
                    @Parameter(
                            schema =
                                    @Schema(
                                            allowableValues = {
                                                "parts",
                                                "partners",
                                                "sites",
                                                "relations",
                                                "part-mappings"
                                            }))
                    String entity,
            @RequestPart("file") MultipartFile file,
            @RequestPart(value = "mapping", required = false)
                    @Parameter(description = "JSON object {column: field|null}; default: suggested mapping")
                    String mapping) {
        return ImportPreviewResponse.from(
                imports.preview(entity(entity), file.getOriginalFilename(), bytes(file), mapping(mapping)));
    }

    @PostMapping(value = "/execute", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            operationId = "executeImport",
            summary = "Import a file (upsert by business key; all-or-nothing unless skipErrors)",
            responses = {
                @ApiResponse(responseCode = "200", description = "Imported"),
                @ApiResponse(
                        responseCode = "422",
                        description = "Rows failed, nothing imported",
                        content = @Content(schema = @Schema(implementation = ImportResultResponse.class)))
            })
    public ResponseEntity<ImportResultResponse> execute(
            @PathVariable
                    @Parameter(
                            schema =
                                    @Schema(
                                            allowableValues = {
                                                "parts",
                                                "partners",
                                                "sites",
                                                "relations",
                                                "part-mappings"
                                            }))
                    String entity,
            @RequestPart("file") MultipartFile file,
            @RequestPart(value = "mapping", required = false) String mapping,
            @RequestParam(defaultValue = "false") boolean skipErrors) {
        try {
            return ResponseEntity.ok(ImportResultResponse.from(imports.execute(
                    entity(entity), file.getOriginalFilename(), bytes(file), mapping(mapping), skipErrors)));
        } catch (ImportFailedException ex) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(ImportResultResponse.from(ex.result()));
        }
    }

    @PostMapping(value = "/error-report", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = TEXT_CSV)
    @Operation(
            operationId = "importErrorReport",
            summary = "Rows with errors as CSV (original columns plus row number and messages)",
            responses =
                    @ApiResponse(
                            responseCode = "200",
                            content =
                                    @Content(
                                            mediaType = TEXT_CSV,
                                            schema = @Schema(type = "string", format = "binary"))))
    public ResponseEntity<byte[]> errorReport(
            @PathVariable
                    @Parameter(
                            schema =
                                    @Schema(
                                            allowableValues = {
                                                "parts",
                                                "partners",
                                                "sites",
                                                "relations",
                                                "part-mappings"
                                            }))
                    String entity,
            @RequestPart("file") MultipartFile file,
            @RequestPart(value = "mapping", required = false) String mapping) {
        ImportEntity importEntity = entity(entity);
        var report = imports.errorReport(importEntity, file.getOriginalFilename(), bytes(file), mapping(mapping));
        byte[] csv = toCsv(report.columns(), report.rows());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(TEXT_CSV + ";charset=UTF-8"))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(importEntity.pathValue() + "-errors.csv")
                                .build()
                                .toString())
                .body(csv);
    }

    static byte[] toCsv(List<String> columns, List<ImportRow> rows) {
        var out = new StringWriter();
        out.write('﻿'); // BOM so that Excel detects UTF-8
        List<String> header = new ArrayList<>();
        header.add("rowNumber");
        header.addAll(columns);
        header.add("errors");
        try (var printer = new CSVPrinter(
                out, CSVFormat.DEFAULT.builder().setDelimiter(';').get())) {
            printer.printRecord(header);
            for (ImportRow row : rows) {
                List<String> record = new ArrayList<>();
                record.add(String.valueOf(row.rowNumber()));
                for (int i = 0; i < columns.size(); i++) {
                    record.add(i < row.cells().size() ? row.cells().get(i) : "");
                }
                record.add(
                        row.messages().stream().map(ImportController::describe).collect(Collectors.joining(" | ")));
                printer.printRecord(record);
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String describe(ImportMessage message) {
        return (message.field() == null ? "" : message.field() + ": ") + message.code() + " - " + message.message();
    }

    private static ImportEntity entity(String value) {
        return ImportEntity.fromPath(value).orElseThrow(() -> new NotFoundException("Import entity", value));
    }

    private static byte[] bytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw new PpapException(PpapException.Kind.INVALID, "UNREADABLE_FILE", "Upload could not be read");
        }
    }

    private Map<String, String> mapping(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            Map<String, String> parsed = jsonMapper.readValue(json, MAPPING_TYPE);
            return parsed == null ? null : new LinkedHashMap<>(parsed);
        } catch (JacksonException ex) {
            throw ValidationException.of(
                    "mapping", ViolationCodes.INVALID_FORMAT, "must be a JSON object {column: field}");
        }
    }
}
