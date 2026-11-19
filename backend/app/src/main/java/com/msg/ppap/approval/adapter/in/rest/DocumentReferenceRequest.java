package com.msg.ppap.approval.adapter.in.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** Reference to an uploaded document. */
public record DocumentReferenceRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID documentId) {}
