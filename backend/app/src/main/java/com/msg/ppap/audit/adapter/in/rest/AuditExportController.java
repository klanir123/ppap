package com.msg.ppap.audit.adapter.in.rest;

import com.msg.ppap.audit.application.AuditExportService;
import com.msg.ppap.audit.application.AuditExportService.SignedExport;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Signed audit export of an approval case (UC-11, any authenticated user with access to the case). */
@RestController
@Tag(name = "audit")
public class AuditExportController {

    static final String SIGNATURE = "X-Signature";
    static final String PAYLOAD_HASH = "X-Payload-Hash";

    private final AuditExportService exports;

    public AuditExportController(AuditExportService exports) {
        this.exports = exports;
    }

    @GetMapping(path = "/api/v1/approval-cases/{id}/audit-export", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "exportApprovalCaseAudit",
            summary =
                    "Audit export of a case: case, timeline, submissions, decisions, exchange records and audit events"
                            + " (RFC 8785 canonical JSON, detached JWS in X-Signature)",
            responses =
                    @ApiResponse(
                            responseCode = "200",
                            description = "Export (file audit-<caseNumber>.json)",
                            headers = {
                                @Header(
                                        name = SIGNATURE,
                                        description = "Detached JWS (ES256, b64=false) over the body",
                                        schema = @Schema(type = "string")),
                                @Header(
                                        name = PAYLOAD_HASH,
                                        description = "sha256:<hex> of the body",
                                        schema = @Schema(type = "string"))
                            },
                            content =
                                    @Content(
                                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                                            schema = @Schema(type = "object"))))
    public ResponseEntity<byte[]> export(@PathVariable UUID id) {
        SignedExport export = exports.export(id);
        String fileName = "audit-" + export.caseNumber().replaceAll("[^A-Za-z0-9._-]", "_") + ".json";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .header(SIGNATURE, export.jws())
                .header(PAYLOAD_HASH, export.payloadHash())
                .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, SIGNATURE + ", " + PAYLOAD_HASH)
                .body(export.content());
    }
}
