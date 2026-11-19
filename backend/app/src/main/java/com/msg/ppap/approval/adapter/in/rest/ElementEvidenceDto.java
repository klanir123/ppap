package com.msg.ppap.approval.adapter.in.rest;

import com.msg.ppap.approval.domain.Requirement;
import com.msg.ppap.approval.domain.SelfAssessment;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Evidence of an element in a revision. */
public record ElementEvidenceDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Requirement requirement,
        SelfAssessment selfAssessment,

        @Schema(description = "Structured values (any JSON value), validated against the value schema")
        Object values,

        String comment,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<DocumentRefDto> documents,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean complete,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<IssueDto> issues,

        @Schema(description = "Review finding of the customer (interim or of the decision), null if none")
        FindingDto finding) {}
