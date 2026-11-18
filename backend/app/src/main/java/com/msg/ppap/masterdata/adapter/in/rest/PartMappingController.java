package com.msg.ppap.masterdata.adapter.in.rest;

import com.msg.ppap.masterdata.application.PartMappingService;
import com.msg.ppap.masterdata.application.PartMappingView;
import com.msg.ppap.masterdata.domain.PartnerRole;
import com.msg.ppap.platform.web.CreatedResponse;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Part mappings of a relation and the lookup by partner part number (UC-04). */
@RestController
@Tag(name = "relations")
public class PartMappingController {

    static final String PATH = "/api/v1/relations/{relationId}/part-mappings";
    private static final Set<String> SORTABLE = Set.of("partnerPartNumber", "createdAt", "updatedAt");
    private static final Sort DEFAULT_SORT = Sort.by("partnerPartNumber");

    private final PartMappingService mappings;

    public PartMappingController(PartMappingService mappings) {
        this.mappings = mappings;
    }

    @GetMapping(PATH)
    @Operation(operationId = "listPartMappings", summary = "Part mappings of a relation")
    public PartMappingPage list(
            @PathVariable UUID relationId,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(required = false) List<String> sort) {
        var pageable = RestSupport.pageable(page, size, sort, SORTABLE, DEFAULT_SORT);
        return PartMappingPage.of(mappings.search(relationId, q, pageable).map(PartMappingDto::from));
    }

    @GetMapping(PATH + "/{id}")
    @Operation(operationId = "getPartMapping", summary = "Get a part mapping")
    public ResponseEntity<PartMappingDto> get(@PathVariable UUID relationId, @PathVariable UUID id) {
        return respond(mappings.view(relationId, id));
    }

    @PostMapping(PATH)
    @CreatedResponse
    @PreAuthorize(RestSupport.WRITE)
    @Operation(operationId = "createPartMapping", summary = "Create a part mapping")
    public ResponseEntity<PartMappingDto> create(
            @PathVariable UUID relationId, @RequestBody PartMappingRequest request) {
        PartMappingView view = mappings.create(relationId, request.toCommand());
        return RestSupport.created(
                "/api/v1/relations/" + relationId + "/part-mappings",
                view.mapping().id(),
                PartMappingDto.from(view),
                view.mapping().version());
    }

    @PutMapping(PATH + "/{id}")
    @PreAuthorize(RestSupport.WRITE)
    @Operation(operationId = "updatePartMapping", summary = "Replace a part mapping (If-Match for optimistic locking)")
    public ResponseEntity<PartMappingDto> update(
            @PathVariable UUID relationId,
            @PathVariable UUID id,
            @RequestBody PartMappingRequest request,
            @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        return respond(mappings.update(relationId, id, request.toCommand(), RestSupport.expectedVersion(ifMatch)));
    }

    @DeleteMapping(PATH + "/{id}")
    @PreAuthorize(RestSupport.WRITE)
    @Operation(operationId = "deletePartMapping", summary = "Delete a part mapping (physically)")
    public ResponseEntity<Void> delete(
            @PathVariable UUID relationId,
            @PathVariable UUID id,
            @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        mappings.delete(relationId, id, RestSupport.expectedVersion(ifMatch));
        return RestSupport.noContent();
    }

    @GetMapping("/api/v1/part-mappings/resolve")
    @Operation(operationId = "resolvePartMapping", summary = "Find the mapping for a partner part number (404 if none)")
    public ResponseEntity<PartMappingDto> resolve(
            @RequestParam String partnerBpnl,
            @RequestParam PartnerRole partnerRole,
            @RequestParam String partnerPartNumber) {
        return respond(mappings.resolve(partnerBpnl, partnerRole.name(), partnerPartNumber));
    }

    private static ResponseEntity<PartMappingDto> respond(PartMappingView view) {
        return RestSupport.ok(PartMappingDto.from(view), view.mapping().version());
    }
}
