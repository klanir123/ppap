package com.msg.ppap.identity.adapter.in.rest;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * OIDC settings the SPA needs to start the login.
 *
 * @param authority OIDC authority (issuer URL reachable by the browser)
 * @param clientId public client id of the SPA
 */
public record OidcInfo(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String authority,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String clientId) {}
