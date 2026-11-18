package com.msg.ppap.masterdata.adapter.in.rest;

import com.msg.ppap.masterdata.application.ChangeAction;
import com.msg.ppap.masterdata.application.PartService;
import com.msg.ppap.masterdata.domain.Part;
import com.msg.ppap.platform.web.CreatedResponse;
import com.msg.ppap.platform.web.UpsertResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Sort;
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

/** Own parts of the tenant. */
@RestController
@RequestMapping(PartController.PATH)
@Tag(name = "parts")
public class PartController {

    static final String PATH = "/api/v1/parts";
    private static final Set<String> SORTABLE =
            Set.of("partNumber", "nameDe", "nameEn", "drawingNumber", "active", "createdAt", "updatedAt");
    private static final Sort DEFAULT_SORT = Sort.by("partNumber");

    private final PartService parts;

    public PartController(PartService parts) {
        this.parts = parts;
    }

    @GetMapping
    @Operation(operationId = "listParts", summary = "Search parts")
    public PartPage list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String partnerBpnl,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(required = false) List<String> sort) {
        var pageable = RestSupport.pageable(page, size, sort, SORTABLE, DEFAULT_SORT);
        return PartPage.of(parts.search(q, active, partnerBpnl, pageable).map(PartDto::from));
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getPart", summary = "Get a part")
    public ResponseEntity<PartDto> get(@PathVariable UUID id) {
        return respond(parts.get(id));
    }

    @PostMapping
    @CreatedResponse
    @PreAuthorize(RestSupport.WRITE)
    @Operation(operationId = "createPart", summary = "Create a part")
    public ResponseEntity<PartDto> create(@RequestBody PartRequest request) {
        Part part = parts.create(request.toCommand());
        return RestSupport.created(PATH, part.id(), PartDto.from(part), part.version());
    }

    @PutMapping("/{id}")
    @PreAuthorize(RestSupport.WRITE)
    @Operation(operationId = "updatePart", summary = "Replace a part (If-Match for optimistic locking)")
    public ResponseEntity<PartDto> update(
            @PathVariable UUID id,
            @RequestBody PartRequest request,
            @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        return respond(parts.update(id, request.toCommand(), RestSupport.expectedVersion(ifMatch)));
    }

    @PutMapping("/by-number/{partNumber}")
    @UpsertResponse
    @PreAuthorize(RestSupport.WRITE)
    @Operation(operationId = "upsertPartByNumber", summary = "Create or replace a part by part number (idempotent)")
    public ResponseEntity<PartDto> upsert(
            @PathVariable String partNumber,
            @RequestBody PartRequest request,
            @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        var result = parts.upsertByPartNumber(partNumber, request.toCommand(), RestSupport.expectedVersion(ifMatch));
        Part part = result.value();
        return RestSupport.upserted(
                result.action() == ChangeAction.CREATED, PATH, part.id(), PartDto.from(part), part.version());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(RestSupport.WRITE)
    @Operation(
            operationId = "deletePart",
            summary = "Delete a part (physically if never referenced, otherwise deactivate)")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id, @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        parts.delete(id, RestSupport.expectedVersion(ifMatch));
        return RestSupport.noContent();
    }

    private static ResponseEntity<PartDto> respond(Part part) {
        return RestSupport.ok(PartDto.from(part), part.version());
    }
}
