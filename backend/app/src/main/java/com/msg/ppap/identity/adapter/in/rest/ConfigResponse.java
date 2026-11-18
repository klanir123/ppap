package com.msg.ppap.identity.adapter.in.rest;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Public runtime configuration for the SPA, loaded before login.
 *
 * @param participant participant operating this connector
 * @param oidc login settings
 * @param defaultLocale default UI locale
 * @param version backend build version
 */
public record ConfigResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ParticipantInfo participant,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) OidcInfo oidc,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String defaultLocale,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String version) {}
