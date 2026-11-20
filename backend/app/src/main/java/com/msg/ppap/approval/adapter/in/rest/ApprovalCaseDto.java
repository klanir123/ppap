package com.msg.ppap.approval.adapter.in.rest;

import com.msg.ppap.approval.domain.CaseAction;
import com.msg.ppap.approval.domain.CaseRole;
import com.msg.ppap.approval.domain.CaseSource;
import com.msg.ppap.approval.domain.CaseStatus;
import com.msg.ppap.approval.domain.DecisionResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Approval case (list form). */
public record ApprovalCaseDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String caseNumber,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Exchange id shared with the partner's case")
        UUID exchangeId,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) CaseRole ownRole,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) CaseSource source,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) CaseStatus status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) CasePartnerDto partner,
        UUID relationId,

        @Schema(description = "Own part; null while the part of a received request is not mapped (clarification)")
        CasePartDto part,

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
        DecisionResult lastDecisionResult,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Actions the current user may perform")
        List<CaseAction> allowedActions,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long version,
        Instant createdAt,
        Instant updatedAt,

        @Schema(description = "Open clarification (UC-04 A1), null if none")
        ClarificationDto clarification,

        @Schema(description = "Latest message sent to the partner, null if none")
        LastOutboundDto lastOutbound,

        @Schema(description = "Parent case id of a sub-supplier request (UC-09), null otherwise")
        UUID parentCaseId) {}
