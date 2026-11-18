package com.msg.ppap.masterdata.adapter.in.rest;

import com.msg.ppap.masterdata.application.ChangeAction;
import com.msg.ppap.masterdata.application.PartnerService;
import com.msg.ppap.masterdata.domain.Partner;
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

/** Business partners of the tenant. */
@RestController
@RequestMapping(PartnerController.PATH)
@Tag(name = "partners")
public class PartnerController {

    static final String PATH = "/api/v1/partners";
    private static final Set<String> SORTABLE = Set.of("bpnl", "name", "active", "createdAt", "updatedAt");
    private static final Sort DEFAULT_SORT = Sort.by("name");

    private final PartnerService partners;

    public PartnerController(PartnerService partners) {
        this.partners = partners;
    }

    @GetMapping
    @Operation(operationId = "listPartners", summary = "Search partners")
    public PartnerPage list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(required = false) List<String> sort) {
        var pageable = RestSupport.pageable(page, size, sort, SORTABLE, DEFAULT_SORT);
        return PartnerPage.of(partners.search(q, active, pageable).map(PartnerDto::from));
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getPartner", summary = "Get a partner")
    public ResponseEntity<PartnerDto> get(@PathVariable UUID id) {
        return respond(partners.get(id));
    }

    @PostMapping
    @CreatedResponse
    @PreAuthorize(RestSupport.WRITE_PARTNERS)
    @Operation(operationId = "createPartner", summary = "Create a partner")
    public ResponseEntity<PartnerDto> create(@RequestBody PartnerRequest request) {
        Partner partner = partners.create(request.toCommand());
        return RestSupport.created(PATH, partner.id(), PartnerDto.from(partner), partner.version());
    }

    @PutMapping("/{id}")
    @PreAuthorize(RestSupport.WRITE_PARTNERS)
    @Operation(operationId = "updatePartner", summary = "Replace a partner (If-Match for optimistic locking)")
    public ResponseEntity<PartnerDto> update(
            @PathVariable UUID id,
            @RequestBody PartnerRequest request,
            @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        return respond(partners.update(id, request.toCommand(), RestSupport.expectedVersion(ifMatch)));
    }

    @PutMapping("/by-bpnl/{bpnl}")
    @UpsertResponse
    @PreAuthorize(RestSupport.WRITE_PARTNERS)
    @Operation(operationId = "upsertPartnerByBpnl", summary = "Create or replace a partner by BPNL (idempotent)")
    public ResponseEntity<PartnerDto> upsert(
            @PathVariable String bpnl,
            @RequestBody PartnerRequest request,
            @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        var result = partners.upsertByBpnl(bpnl, request.toCommand(), RestSupport.expectedVersion(ifMatch));
        Partner partner = result.value();
        return RestSupport.upserted(
                result.action() == ChangeAction.CREATED,
                PATH,
                partner.id(),
                PartnerDto.from(partner),
                partner.version());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(RestSupport.WRITE_PARTNERS)
    @Operation(
            operationId = "deletePartner",
            summary = "Delete a partner (physically if never referenced, otherwise deactivate)")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id, @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        partners.delete(id, RestSupport.expectedVersion(ifMatch));
        return RestSupport.noContent();
    }

    private static ResponseEntity<PartnerDto> respond(Partner partner) {
        return RestSupport.ok(PartnerDto.from(partner), partner.version());
    }
}
