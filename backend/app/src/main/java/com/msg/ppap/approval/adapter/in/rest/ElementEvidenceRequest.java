package com.msg.ppap.approval.adapter.in.rest;

import com.msg.ppap.approval.domain.SelfAssessment;
import io.swagger.v3.oas.annotations.media.Schema;

/** Evidence of an element of the draft revision. */
public record ElementEvidenceRequest(
        @Schema(
                types = {"string", "null"},
                description = "OK, NOK or NA (NOK/NA require a comment); null clears the assessment")
        SelfAssessment selfAssessment,

        @Schema(description = "Structured values (any JSON value), validated against the value schema")
        Object values,

        String comment) {}
