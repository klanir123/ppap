package com.msg.ppap.exchange.adapter.in.rest;

import com.msg.ppap.exchange.application.DocumentTransferService;
import com.msg.ppap.exchange.application.PullJobs;
import com.msg.ppap.exchange.application.port.out.DocumentTransferStore.Transfer;
import com.msg.ppap.platform.domain.FieldViolation;
import com.msg.ppap.platform.domain.ValidationException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** Manual re-fetch of a partner document of a case (docs/api/pull-api.md, „REST-Ergänzungen“). */
@RestController
@Tag(name = "approval-cases")
public class CaseDocumentController {

    private final DocumentTransferService transfers;
    private final PullJobs jobs;

    public CaseDocumentController(DocumentTransferService transfers, PullJobs jobs) {
        this.transfers = transfers;
        this.jobs = jobs;
    }

    /** Transfer state of a partner document. */
    public record DocumentTransferDto(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String sha256,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String fileName,

            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    allowableValues = {"PENDING", "FETCHED", "FAILED", "NOT_AVAILABLE"})
            String transferStatus,

            @Schema(
                    types = {"string", "null"},
                    format = "uuid")
            UUID documentId,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            boolean hashVerified,

            @Schema(description = "Error code of a FAILED transfer, otherwise null")
            String transferError,

            String lastError,
            Instant updatedAt) {

        static DocumentTransferDto of(Transfer t) {
            return new DocumentTransferDto(
                    t.sha256(),
                    t.fileName(),
                    t.status(),
                    t.documentId(),
                    t.hashVerified(),
                    "FAILED".equals(t.status()) ? t.errorCode() : null,
                    t.lastError(),
                    t.updatedAt());
        }
    }

    @PostMapping("/api/v1/approval-cases/{id}/documents/{sha256}/fetch")
    @PreAuthorize(
            "hasAnyRole('CUSTOMER_QUALITY_ENGINEER', 'SUPPLIER_QUALITY_MANAGER', 'CONNECTOR_ADMIN', 'SYSTEM_INTEGRATION')")
    @Operation(
            operationId = "fetchApprovalCaseDocument",
            summary = "Fetch a partner document of the case (again) via EDC; no effect if fetched or pending")
    public ResponseEntity<DocumentTransferDto> fetch(@PathVariable UUID id, @PathVariable String sha256) {
        if (sha256 == null || !sha256.matches("^[0-9a-f]{64}$")) {
            throw new ValidationException(
                    "INVALID_SHA256",
                    List.of(new FieldViolation(
                            "sha256", "INVALID_FORMAT", "plain lower-case hex SHA-256 (64 characters) expected")));
        }
        Transfer transfer = transfers.refetch(id, sha256);
        jobs.nudge();
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(DocumentTransferDto.of(transfer));
    }
}
