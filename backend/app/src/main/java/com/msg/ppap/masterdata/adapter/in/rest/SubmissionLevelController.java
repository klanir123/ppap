package com.msg.ppap.masterdata.adapter.in.rest;

import com.msg.ppap.masterdata.application.ChangeAction;
import com.msg.ppap.masterdata.application.SubmissionLevelService;
import com.msg.ppap.masterdata.domain.SubmissionLevel;
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

/** Catalog: submission levels (UC-02). */
@RestController
@RequestMapping(SubmissionLevelController.PATH)
@Tag(name = "catalog")
public class SubmissionLevelController {

    static final String PATH = "/api/v1/catalog/levels";

    private final SubmissionLevelService service;

    public SubmissionLevelController(SubmissionLevelService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(
            operationId = "listSubmissionLevels",
            summary = "List submission levels (sorted by sortOrder, not paginated)")
    public List<SubmissionLevelDto> list(@RequestParam(required = false) Boolean active) {
        return service.list(active).stream().map(SubmissionLevelDto::from).toList();
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getSubmissionLevel", summary = "Get one of the submission levels")
    public ResponseEntity<SubmissionLevelDto> get(@PathVariable UUID id) {
        return respond(service.get(id));
    }

    @PostMapping
    @CreatedResponse
    @PreAuthorize(RestSupport.WRITE)
    @Operation(operationId = "createSubmissionLevel", summary = "Create one of the submission levels")
    public ResponseEntity<SubmissionLevelDto> create(@RequestBody SubmissionLevelRequest request) {
        SubmissionLevel created = service.create(request.toCommand());
        return RestSupport.created(PATH, created.id(), SubmissionLevelDto.from(created), created.version());
    }

    @PutMapping("/{id}")
    @PreAuthorize(RestSupport.WRITE)
    @Operation(
            operationId = "updateSubmissionLevel",
            summary = "Replace one of the submission levels (If-Match for optimistic locking)")
    public ResponseEntity<SubmissionLevelDto> update(
            @PathVariable UUID id,
            @RequestBody SubmissionLevelRequest request,
            @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        return respond(service.update(id, request.toCommand(), RestSupport.expectedVersion(ifMatch)));
    }

    @PutMapping("/by-code/{code}")
    @UpsertResponse
    @PreAuthorize(RestSupport.WRITE)
    @Operation(
            operationId = "upsertSubmissionLevelByCode",
            summary = "Create or replace one of the submission levels by code (idempotent)")
    public ResponseEntity<SubmissionLevelDto> upsert(
            @PathVariable String code,
            @RequestBody SubmissionLevelRequest request,
            @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        var result = service.upsertByCode(code, request.toCommand(), RestSupport.expectedVersion(ifMatch));
        SubmissionLevel value = result.value();
        return RestSupport.upserted(
                result.action() == ChangeAction.CREATED,
                PATH,
                value.id(),
                SubmissionLevelDto.from(value),
                value.version());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(RestSupport.WRITE)
    @Operation(
            operationId = "deleteSubmissionLevel",
            summary = "Delete one of the submission levels (physically if never referenced, otherwise deactivate)")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id, @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        service.delete(id, RestSupport.expectedVersion(ifMatch));
        return RestSupport.noContent();
    }

    private static ResponseEntity<SubmissionLevelDto> respond(SubmissionLevel value) {
        return RestSupport.ok(SubmissionLevelDto.from(value), value.version());
    }
}
