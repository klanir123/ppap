package com.msg.ppap.exchange.adapter.in.rest;

import com.msg.ppap.exchange.adapter.in.rest.ConnectorDtos.PolicyPreviewDto;
import com.msg.ppap.exchange.application.PolicyPreviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * ODRL preview of a policy profile (UC-10). Lives in the exchange module because the ODRL/JSON-LD rendering belongs
 * to the dataspace connector; the profile itself is master data ({@code /api/v1/policy-profiles}).
 */
@RestController
@Tag(name = "policy-profiles")
public class PolicyPreviewController {

    private final PolicyPreviewService previews;

    public PolicyPreviewController(PolicyPreviewService previews) {
        this.previews = previews;
    }

    @GetMapping("/api/v1/policy-profiles/{id}/preview")
    @Operation(
            operationId = "previewPolicyProfile",
            summary = "ODRL policies (JSON-LD) the profile produces for the own offers")
    public PolicyPreviewDto preview(@PathVariable UUID id) {
        return ConnectorDtos.preview(previews.preview(id));
    }
}
