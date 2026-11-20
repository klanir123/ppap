package com.msg.ppap.masterdata.adapter.in.rest;

import com.msg.ppap.masterdata.api.OfferReconciler;
import com.msg.ppap.masterdata.application.PolicyProfileService;
import com.msg.ppap.masterdata.domain.PolicyProfile;
import com.msg.ppap.platform.web.CreatedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
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

/**
 * Policy profiles of relations (UC-10): read for every authenticated user (relation form), write for connector
 * administrators. Changes are applied to the own EDC by the provisioning reconciliation.
 */
@RestController
@RequestMapping(PolicyProfileController.PATH)
@Tag(name = "policy-profiles")
public class PolicyProfileController {

    static final String PATH = "/api/v1/policy-profiles";
    static final String WRITE = "hasRole('CONNECTOR_ADMIN')";

    private final PolicyProfileService service;
    private final ObjectProvider<OfferReconciler> reconciler;

    public PolicyProfileController(PolicyProfileService service, ObjectProvider<OfferReconciler> reconciler) {
        this.service = service;
        this.reconciler = reconciler;
    }

    @GetMapping
    @Operation(operationId = "listPolicyProfiles", summary = "Policy profiles, sorted by code")
    public List<PolicyProfileDto> list(@RequestParam(required = false) Boolean active) {
        return service.list(active).stream().map(PolicyProfileDto::from).toList();
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getPolicyProfile", summary = "Get a policy profile")
    public ResponseEntity<PolicyProfileDto> get(@PathVariable UUID id) {
        return respond(service.get(id));
    }

    @PostMapping
    @CreatedResponse
    @PreAuthorize(WRITE)
    @Operation(operationId = "createPolicyProfile", summary = "Create a policy profile")
    public ResponseEntity<PolicyProfileDto> create(@RequestBody PolicyProfileRequest request) {
        PolicyProfile created = service.create(request.toCommand());
        return RestSupport.created(
                PATH,
                created.id(),
                PolicyProfileDto.from(created, ReconciliationDto.run(reconciler)),
                created.version());
    }

    @PutMapping("/{id}")
    @PreAuthorize(WRITE)
    @Operation(
            operationId = "updatePolicyProfile",
            summary = "Replace a policy profile (If-Match); the EDC offers are reconciled afterwards")
    public ResponseEntity<PolicyProfileDto> update(
            @PathVariable UUID id,
            @RequestBody PolicyProfileRequest request,
            @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        PolicyProfile updated = service.update(id, request.toCommand(), RestSupport.expectedVersion(ifMatch));
        return RestSupport.ok(PolicyProfileDto.from(updated, ReconciliationDto.run(reconciler)), updated.version());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(WRITE)
    @Operation(
            operationId = "deletePolicyProfile",
            summary =
                    "Delete a policy profile (physically if no relation uses it, otherwise deactivate; not 'default')")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id, @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        service.delete(id, RestSupport.expectedVersion(ifMatch));
        return RestSupport.noContent();
    }

    private static ResponseEntity<PolicyProfileDto> respond(PolicyProfile value) {
        return RestSupport.ok(PolicyProfileDto.from(value), value.version());
    }
}
