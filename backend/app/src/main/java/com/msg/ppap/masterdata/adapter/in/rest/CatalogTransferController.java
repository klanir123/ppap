package com.msg.ppap.masterdata.adapter.in.rest;

import com.msg.ppap.masterdata.application.CatalogTransferService;
import com.msg.ppap.masterdata.application.imports.ImportFailedException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Export and import of the complete catalog (UC-02 A1). */
@RestController
@RequestMapping("/api/v1/catalog")
@Tag(name = "catalog")
public class CatalogTransferController {

    private final CatalogTransferService transfer;

    public CatalogTransferController(CatalogTransferService transfer) {
        this.transfer = transfer;
    }

    @GetMapping("/export")
    @Operation(operationId = "exportCatalog", summary = "Export elements, levels and reasons")
    public CatalogExport export() {
        return CatalogExport.from(transfer.export());
    }

    @PostMapping("/import")
    @PreAuthorize(RestSupport.WRITE)
    @Operation(
            operationId = "importCatalog",
            summary = "Import a catalog (upsert by code; all-or-nothing unless skipErrors)",
            responses = {
                @ApiResponse(responseCode = "200", description = "Imported"),
                @ApiResponse(
                        responseCode = "422",
                        description = "Entries failed, nothing imported",
                        content = @Content(schema = @Schema(implementation = ImportResultResponse.class)))
            })
    public ResponseEntity<ImportResultResponse> importCatalog(
            @RequestBody CatalogExport catalog, @RequestParam(defaultValue = "false") boolean skipErrors) {
        try {
            return ResponseEntity.ok(
                    ImportResultResponse.from(transfer.importCatalog(catalog.toCommand(), skipErrors)));
        } catch (ImportFailedException ex) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(ImportResultResponse.from(ex.result()));
        }
    }
}
