package com.msg.ppap.twin.adapter.in.rest;

import com.msg.ppap.platform.domain.NotFoundException;
import com.msg.ppap.twin.application.TwinRegistrationService;
import com.msg.ppap.twin.application.port.out.TwinStore.StoredTwin;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;

/** Registered digital twin of a case in the own DTR (supplier, diagnosis/cockpit; docs/api/pull-api.md). */
@RestController
@RequestMapping("/api/v1/twins")
@Tag(name = "twins")
@PreAuthorize("hasAnyRole('SUPPLIER_QUALITY_MANAGER', 'CONNECTOR_ADMIN', 'SYSTEM_INTEGRATION')")
public class TwinController {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final TwinRegistrationService twins;

    public TwinController(TwinRegistrationService twins) {
        this.twins = twins;
    }

    /** Registered twin. */
    public record TwinDto(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            UUID exchangeId,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            UUID caseId,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            Instant registeredAt,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            Instant updatedAt,

            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "AAS 3.0 ShellDescriptor exactly as registered in the DTR (incl."
                            + " specificAssetIds and submodelDescriptors)")
            Map<String, Object> shell) {}

    @GetMapping("/{exchangeId}")
    @Operation(operationId = "getTwin", summary = "Digital twin (AAS shell descriptor) registered for a case")
    @SuppressWarnings("unchecked")
    public TwinDto twin(@PathVariable UUID exchangeId) {
        StoredTwin twin = twins.find(exchangeId).orElseThrow(() -> new NotFoundException("Twin", exchangeId));
        return new TwinDto(
                twin.exchangeId(),
                twin.caseId(),
                twin.registeredAt(),
                twin.updatedAt(),
                JSON.readValue(twin.shell(), Map.class));
    }
}
