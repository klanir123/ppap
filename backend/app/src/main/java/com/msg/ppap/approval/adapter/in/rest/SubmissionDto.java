package com.msg.ppap.approval.adapter.in.rest;

import com.msg.ppap.approval.domain.ExchangeMode;
import com.msg.ppap.approval.domain.SubmissionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/** Revision of the submission. */
public record SubmissionDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int revision,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) SubmissionStatus status,
        Instant submittedAt,
        String submittedBy,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ElementEvidenceDto> elements,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) CompletenessDto completeness,

        @Schema(description = "sha256:<hex> of the canonical payload (SUBMITTED only)")
        String payloadHash,

        SignatureDto signature,
        DecisionDto decision,
        Integer basedOnRevision,

        @Schema(
                description = "Effective exchange mode of the revision (supplier: how it was provided, customer: how"
                        + " it was received); null for drafts and local cases")
        ExchangeMode exchangeMode) {}
