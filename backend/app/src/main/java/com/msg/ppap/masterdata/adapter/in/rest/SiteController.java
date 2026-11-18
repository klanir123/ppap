package com.msg.ppap.masterdata.adapter.in.rest;

import com.msg.ppap.masterdata.application.ChangeAction;
import com.msg.ppap.masterdata.application.SiteService;
import com.msg.ppap.masterdata.application.SiteView;
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

/** Sites of the tenant and its partners. */
@RestController
@RequestMapping(SiteController.PATH)
@Tag(name = "sites")
public class SiteController {

    static final String PATH = "/api/v1/sites";
    private static final Set<String> SORTABLE =
            Set.of("bpns", "name", "ownerBpnl", "city", "country", "active", "createdAt", "updatedAt");
    private static final Sort DEFAULT_SORT = Sort.by("name");

    private final SiteService sites;

    public SiteController(SiteService sites) {
        this.sites = sites;
    }

    @GetMapping
    @Operation(operationId = "listSites", summary = "Search sites")
    public SitePage list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String ownerBpnl,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(required = false) List<String> sort) {
        var pageable = RestSupport.pageable(page, size, sort, SORTABLE, DEFAULT_SORT);
        return SitePage.of(sites.search(q, active, ownerBpnl, pageable).map(SiteDto::from));
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getSite", summary = "Get a site")
    public ResponseEntity<SiteDto> get(@PathVariable UUID id) {
        return respond(sites.view(id));
    }

    @PostMapping
    @CreatedResponse
    @PreAuthorize(RestSupport.WRITE)
    @Operation(operationId = "createSite", summary = "Create a site")
    public ResponseEntity<SiteDto> create(@RequestBody SiteRequest request) {
        SiteView view = sites.create(request.toCommand());
        return RestSupport.created(
                PATH, view.site().id(), SiteDto.from(view), view.site().version());
    }

    @PutMapping("/{id}")
    @PreAuthorize(RestSupport.WRITE)
    @Operation(operationId = "updateSite", summary = "Replace a site (If-Match for optimistic locking)")
    public ResponseEntity<SiteDto> update(
            @PathVariable UUID id,
            @RequestBody SiteRequest request,
            @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        return respond(sites.update(id, request.toCommand(), RestSupport.expectedVersion(ifMatch)));
    }

    @PutMapping("/by-bpns/{bpns}")
    @UpsertResponse
    @PreAuthorize(RestSupport.WRITE)
    @Operation(operationId = "upsertSiteByBpns", summary = "Create or replace a site by BPNS (idempotent)")
    public ResponseEntity<SiteDto> upsert(
            @PathVariable String bpns,
            @RequestBody SiteRequest request,
            @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        var result = sites.upsertByBpns(bpns, request.toCommand(), RestSupport.expectedVersion(ifMatch));
        SiteView view = result.value();
        return RestSupport.upserted(
                result.action() == ChangeAction.CREATED,
                PATH,
                view.site().id(),
                SiteDto.from(view),
                view.site().version());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(RestSupport.WRITE)
    @Operation(
            operationId = "deleteSite",
            summary = "Delete a site (physically if never referenced, otherwise deactivate)")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id, @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        sites.delete(id, RestSupport.expectedVersion(ifMatch));
        return RestSupport.noContent();
    }

    private static ResponseEntity<SiteDto> respond(SiteView view) {
        return RestSupport.ok(SiteDto.from(view), view.site().version());
    }
}
