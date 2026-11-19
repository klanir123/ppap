package com.msg.ppap.audit.adapter.in.rest;

import com.msg.ppap.audit.application.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read access to the audit trail (any authenticated user). */
@RestController
@Tag(name = "audit")
public class AuditController {

    static final int MAX_PAGE_SIZE = 200;

    private final AuditService audit;

    public AuditController(AuditService audit) {
        this.audit = audit;
    }

    @GetMapping("/api/v1/audit-events")
    @Operation(operationId = "listAuditEvents", summary = "Audit trail, newest first")
    public AuditEventPage list(
            @RequestParam(required = false)
                    @Parameter(
                            schema =
                                    @Schema(
                                            allowableValues = {
                                                "PART",
                                                "PARTNER",
                                                "SITE",
                                                "RELATION",
                                                "PART_MAPPING",
                                                "CATALOG_ELEMENT",
                                                "SUBMISSION_LEVEL",
                                                "SUBMISSION_REASON",
                                                "SEED",
                                                "APPROVAL_CASE",
                                                "DOCUMENT"
                                            }))
                    String objectType,
            @RequestParam(required = false) String objectId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE));
        return AuditEventPage.of(audit.search(objectType, objectId, pageable).map(AuditEventDto::from));
    }
}
