package com.msg.ppap.masterdata.adapter.in.rest;

import com.msg.ppap.masterdata.application.RelationService;
import com.msg.ppap.masterdata.application.RelationView;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Customer-supplier relations of the tenant. */
@RestController
@RequestMapping(RelationController.PATH)
@Tag(name = "relations")
public class RelationController {

    static final String PATH = "/api/v1/relations";
    private static final Set<String> SORTABLE =
            Set.of("partnerBpnl", "partnerRole", "exchangeMode", "active", "createdAt", "updatedAt");
    private static final Sort DEFAULT_SORT = Sort.by("partnerBpnl", "partnerRole");

    private final RelationService relations;

    public RelationController(RelationService relations) {
        this.relations = relations;
    }

    @GetMapping
    @Operation(operationId = "listRelations", summary = "Search relations")
    public RelationPage list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String partnerBpnl,
            @RequestParam(required = false) PartnerRole partnerRole,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(required = false) List<String> sort) {
        var pageable = RestSupport.pageable(page, size, sort, SORTABLE, DEFAULT_SORT);
        return RelationPage.of(relations
                .search(q, active, partnerBpnl, partnerRole == null ? null : partnerRole.name(), pageable)
                .map(RelationDto::from));
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getRelation", summary = "Get a relation")
    public ResponseEntity<RelationDto> get(@PathVariable UUID id) {
        return respond(relations.view(id));
    }

    @PostMapping
    @CreatedResponse
    @PreAuthorize(RestSupport.WRITE)
    @Operation(operationId = "createRelation", summary = "Create a relation")
    public ResponseEntity<RelationDto> create(@RequestBody RelationRequest request) {
        RelationView view = relations.create(request.toCommand());
        return RestSupport.created(
                PATH,
                view.relation().id(),
                RelationDto.from(view),
                view.relation().version());
    }

    @PutMapping("/{id}")
    @PreAuthorize(RestSupport.WRITE)
    @Operation(operationId = "updateRelation", summary = "Replace a relation (If-Match for optimistic locking)")
    public ResponseEntity<RelationDto> update(
            @PathVariable UUID id,
            @RequestBody RelationRequest request,
            @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        return respond(relations.update(id, request.toCommand(), RestSupport.expectedVersion(ifMatch)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(RestSupport.WRITE)
    @Operation(
            operationId = "deleteRelation",
            summary = "Delete a relation (physically if it has no part mappings, otherwise deactivate)")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id, @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        relations.delete(id, RestSupport.expectedVersion(ifMatch));
        return RestSupport.noContent();
    }

    private static ResponseEntity<RelationDto> respond(RelationView view) {
        return RestSupport.ok(RelationDto.from(view), view.relation().version());
    }
}
