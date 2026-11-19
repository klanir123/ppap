package com.msg.ppap.approval.adapter.in.rest;

import com.msg.ppap.approval.domain.DecisionResult;
import com.msg.ppap.approval.domain.ExchangeMode;
import com.msg.ppap.approval.domain.SubmissionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** Revision in short form. */
public record SubmissionSummaryDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int revision,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) SubmissionStatus status,
        Instant submittedAt,
        String submittedBy,
        String payloadHash,
        SignatureDto signature,
        DecisionResult decisionResult,
        Integer basedOnRevision,

        @Schema(
                description = "Effective exchange mode of the revision (supplier: how it was provided, customer: how"
                        + " it was received); null for drafts and local cases")
        ExchangeMode exchangeMode) {}
