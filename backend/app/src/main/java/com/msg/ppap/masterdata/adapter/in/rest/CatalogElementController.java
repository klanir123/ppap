package com.msg.ppap.masterdata.adapter.in.rest;

import com.msg.ppap.masterdata.application.CatalogElementService;
import com.msg.ppap.masterdata.application.ChangeAction;
import com.msg.ppap.masterdata.domain.CatalogElement;
import com.msg.ppap.platform.web.CreatedResponse;
import com.msg.ppap.platform.web.UpsertResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Catalog: submission elements (UC-02). */
@RestController
@RequestMapping(CatalogElementController.PATH)
@Tag(name = "catalog")
public class CatalogElementController {

    static final String PATH = "/api/v1/catalog/elements";

    private final CatalogElementService service;

    public CatalogElementController(CatalogElementService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(
            operationId = "listCatalogElements",
            summary = "List submission elements (sorted by sortOrder, not paginated)")
    public List<CatalogElementDto> list(@RequestParam(required = false) Boolean active) {
        return service.list(active).stream().map(CatalogElementDto::from).toList();
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getCatalogElement", summary = "Get one of the submission elements")
    public ResponseEntity<CatalogElementDto> get(@PathVariable UUID id) {
        return respond(service.get(id));
    }

    @PostMapping
    @CreatedResponse
    @PreAuthorize(RestSupport.WRITE)
    @Operation(operationId = "createCatalogElement", summary = "Create one of the submission elements")
    public ResponseEntity<CatalogElementDto> create(@RequestBody CatalogElementRequest request) {
        CatalogElement created = service.create(request.toCommand());
        return RestSupport.created(PATH, created.id(), CatalogElementDto.from(created), created.version());
    }

    @PutMapping("/{id}")
    @PreAuthorize(RestSupport.WRITE)
    @Operation(
            operationId = "updateCatalogElement",
            summary = "Replace one of the submission elements (If-Match for optimistic locking)")
    public ResponseEntity<CatalogElementDto> update(
            @PathVariable UUID id,
            @RequestBody CatalogElementRequest request,
            @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        return respond(service.update(id, request.toCommand(), RestSupport.expectedVersion(ifMatch)));
    }

    @PutMapping("/by-code/{code}")
    @UpsertResponse
    @PreAuthorize(RestSupport.WRITE)
    @Operation(
            operationId = "upsertCatalogElementByCode",
            summary = "Create or replace one of the submission elements by code (idempotent)")
    public ResponseEntity<CatalogElementDto> upsert(
            @PathVariable String code,
            @RequestBody CatalogElementRequest request,
            @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        var result = service.upsertByCode(code, request.toCommand(), RestSupport.expectedVersion(ifMatch));
        CatalogElement value = result.value();
        return RestSupport.upserted(
                result.action() == ChangeAction.CREATED,
                PATH,
                value.id(),
                CatalogElementDto.from(value),
                value.version());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(RestSupport.WRITE)
    @Operation(
            operationId = "deleteCatalogElement",
            summary = "Delete one of the submission elements (physically if never referenced, otherwise deactivate)")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id, @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        service.delete(id, RestSupport.expectedVersion(ifMatch));
        return RestSupport.noContent();
    }

    private static ResponseEntity<CatalogElementDto> respond(CatalogElement value) {
        return RestSupport.ok(CatalogElementDto.from(value), value.version());
    }
}
