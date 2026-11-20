package com.msg.ppap.approval.adapter.in.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

/** Sub-supplier references of the draft revision (UC-09): own, approved child cases; empty list removes all. */
public record SubSupplierReferencesRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<UUID> childCaseIds) {}
