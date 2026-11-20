package com.msg.ppap.exchange.adapter.in.rest;

import com.msg.ppap.exchange.adapter.in.rest.ConnectorDtos.CatalogDto;
import com.msg.ppap.exchange.adapter.in.rest.ConnectorDtos.CatalogRequestDto;
import com.msg.ppap.exchange.adapter.in.rest.ConnectorDtos.ConnectorOverviewDto;
import com.msg.ppap.exchange.adapter.in.rest.ConnectorDtos.EdcObjectDto;
import com.msg.ppap.exchange.adapter.in.rest.ConnectorDtos.EdcProcessDto;
import com.msg.ppap.exchange.application.ConnectorCockpitService;
import com.msg.ppap.platform.domain.ValidationException;
import com.msg.ppap.platform.domain.ViolationCodes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Connector cockpit (UC-10, CONNECTOR_ADMIN): read-only views of the own EDC (management API) and the live partner
 * catalog. Messages, retry and twins remain under {@code /exchange} and {@code /twins}.
 */
@RestController
@RequestMapping("/api/v1/connector")
@Tag(name = "connector")
@PreAuthorize("hasRole('CONNECTOR_ADMIN')")
public class ConnectorController {

    static final int PAGE_SIZE = 25;

    private final ConnectorCockpitService cockpit;

    public ConnectorController(ConnectorCockpitService cockpit) {
        this.cockpit = cockpit;
    }

    @GetMapping("/overview")
    @Operation(
            operationId = "getConnectorOverview",
            summary = "Own EDC, DTR, provisioning, outbox/inbox and EDC object counts")
    public ConnectorOverviewDto overview() {
        return ConnectorDtos.overview(cockpit.overview());
    }

    @GetMapping("/assets")
    @Operation(operationId = "listConnectorAssets", summary = "Assets of the own EDC")
    public List<EdcObjectDto> assets() {
        return cockpit.assets().stream().map(ConnectorDtos::object).toList();
    }

    @GetMapping("/policies")
    @Operation(operationId = "listConnectorPolicies", summary = "Policy definitions of the own EDC")
    public List<EdcObjectDto> policies() {
        return cockpit.policies().stream().map(ConnectorDtos::object).toList();
    }

    @GetMapping("/contract-definitions")
    @Operation(operationId = "listConnectorContractDefinitions", summary = "Contract definitions of the own EDC")
    public List<EdcObjectDto> contractDefinitions() {
        return cockpit.contractDefinitions().stream().map(ConnectorDtos::object).toList();
    }

    @GetMapping("/negotiations")
    @Operation(
            operationId = "listConnectorNegotiations",
            summary = "Contract negotiations of the own EDC (newest first, 25 per page) with partner from master data")
    public List<EdcProcessDto> negotiations(
            @RequestParam(required = false) String state, @RequestParam(defaultValue = "0") int page) {
        return cockpit.negotiations(state, page(page), PAGE_SIZE).stream()
                .map(ConnectorDtos::process)
                .toList();
    }

    @GetMapping("/transfers")
    @Operation(
            operationId = "listConnectorTransfers",
            summary = "Transfer processes of the own EDC (newest first, 25 per page) with partner from master data")
    public List<EdcProcessDto> transfers(
            @RequestParam(required = false) String state, @RequestParam(defaultValue = "0") int page) {
        return cockpit.transfers(state, page(page), PAGE_SIZE).stream()
                .map(ConnectorDtos::process)
                .toList();
    }

    @PostMapping("/catalog")
    @Operation(
            operationId = "requestPartnerCatalog",
            summary = "Live catalog of a partner via DSP (no cache); 422 CATALOG_REQUEST_FAILED with the EDC message")
    public CatalogDto catalog(@RequestBody CatalogRequestDto request) {
        if (request == null
                || request.partnerBpnl() == null
                || request.partnerBpnl().isBlank()) {
            throw ValidationException.of("partnerBpnl", ViolationCodes.REQUIRED, "must not be blank");
        }
        return ConnectorDtos.catalog(cockpit.catalog(request.partnerBpnl().strip()));
    }

    private static int page(int page) {
        if (page < 0) {
            throw ValidationException.of("page", ViolationCodes.INVALID_VALUE, "must be >= 0");
        }
        return page;
    }
}
