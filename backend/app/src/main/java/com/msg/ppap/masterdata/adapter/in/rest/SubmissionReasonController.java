package com.msg.ppap.masterdata.adapter.in.rest;

import com.msg.ppap.masterdata.application.ChangeAction;
import com.msg.ppap.masterdata.application.SubmissionReasonService;
import com.msg.ppap.masterdata.domain.SubmissionReason;
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

/** Catalog: submission reasons (UC-02). */
@RestController
@RequestMapping(SubmissionReasonController.PATH)
@Tag(name = "catalog")
public class SubmissionReasonController {

    static final String PATH = "/api/v1/catalog/reasons";

    private final SubmissionReasonService service;

    public SubmissionReasonController(SubmissionReasonService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(
            operationId = "listSubmissionReasons",
            summary = "List submission reasons (sorted by sortOrder, not paginated)")
    public List<SubmissionReasonDto> list(@RequestParam(required = false) Boolean active) {
        return service.list(active).stream().map(SubmissionReasonDto::from).toList();
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getSubmissionReason", summary = "Get one of the submission reasons")
    public ResponseEntity<SubmissionReasonDto> get(@PathVariable UUID id) {
        return respond(service.get(id));
    }

    @PostMapping
    @CreatedResponse
    @PreAuthorize(RestSupport.WRITE)
    @Operation(operationId = "createSubmissionReason", summary = "Create one of the submission reasons")
    public ResponseEntity<SubmissionReasonDto> create(@RequestBody SubmissionReasonRequest request) {
        SubmissionReason created = service.create(request.toCommand());
        return RestSupport.created(PATH, created.id(), SubmissionReasonDto.from(created), created.version());
    }

    @PutMapping("/{id}")
    @PreAuthorize(RestSupport.WRITE)
    @Operation(
            operationId = "updateSubmissionReason",
            summary = "Replace one of the submission reasons (If-Match for optimistic locking)")
    public ResponseEntity<SubmissionReasonDto> update(
            @PathVariable UUID id,
            @RequestBody SubmissionReasonRequest request,
            @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        return respond(service.update(id, request.toCommand(), RestSupport.expectedVersion(ifMatch)));
    }

    @PutMapping("/by-code/{code}")
    @UpsertResponse
    @PreAuthorize(RestSupport.WRITE)
    @Operation(
            operationId = "upsertSubmissionReasonByCode",
            summary = "Create or replace one of the submission reasons by code (idempotent)")
    public ResponseEntity<SubmissionReasonDto> upsert(
            @PathVariable String code,
            @RequestBody SubmissionReasonRequest request,
            @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        var result = service.upsertByCode(code, request.toCommand(), RestSupport.expectedVersion(ifMatch));
        SubmissionReason value = result.value();
        return RestSupport.upserted(
                result.action() == ChangeAction.CREATED,
                PATH,
                value.id(),
                SubmissionReasonDto.from(value),
                value.version());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(RestSupport.WRITE)
    @Operation(
            operationId = "deleteSubmissionReason",
            summary = "Delete one of the submission reasons (physically if never referenced, otherwise deactivate)")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id, @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        service.delete(id, RestSupport.expectedVersion(ifMatch));
        return RestSupport.noContent();
    }

    private static ResponseEntity<SubmissionReasonDto> respond(SubmissionReason value) {
        return RestSupport.ok(SubmissionReasonDto.from(value), value.version());
    }
}
