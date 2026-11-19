package com.msg.ppap.approval.adapter.in.rest;

import com.msg.ppap.approval.domain.CaseAction;
import com.msg.ppap.approval.domain.CaseRole;
import com.msg.ppap.approval.domain.CaseSource;
import com.msg.ppap.approval.domain.CaseStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Approval case with request, revisions, last decision, review findings and timeline. */
public record ApprovalCaseDetailDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String caseNumber,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Exchange id shared with the partner's case")
        UUID exchangeId,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) CaseRole ownRole,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) CaseSource source,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) CaseStatus status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) CasePartnerDto partner,
        UUID relationId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) CasePartDto part,
        String partnerPartNumber,
        String customerPartNumber,
        String supplierPartNumber,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) CatalogRefDto reason,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) CatalogRefDto level,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDate dueDate,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean overdue,
        String remarks,
        String externalReference,
        Integer currentRevision,
        Instant requestedAt,
        Instant submittedAt,
        Instant decidedAt,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Actions the current user may perform")
        List<CaseAction> allowedActions,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long version,
        Instant createdAt,
        Instant updatedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) CaseRequestDto request,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<SubmissionSummaryDto> submissions,
        @Schema(description = "Latest decision (full)") DecisionDto lastDecision,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Intermediate review results (IN_REVIEW)")
        List<FindingDto> reviewFindings,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<TimelineEntryDto> timeline,

        @Schema(description = "Completeness of the draft revision, if any")
        CompletenessDto draftCompleteness) {}
