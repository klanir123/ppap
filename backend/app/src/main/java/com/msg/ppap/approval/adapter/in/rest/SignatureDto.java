package com.msg.ppap.approval.adapter.in.rest;

import com.msg.ppap.approval.domain.SignatureStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/** Detached JWS (ES256, RFC 7797 unencoded payload) and verification status. */
public record SignatureDto(
        String alg,

        @Schema(description = "RFC 7638 thumbprint of the signing key")
        String kid,

        @Schema(description = "Detached compact JWS <header>..<signature>")
        String jws,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) SignatureStatus status) {}
