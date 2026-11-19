package com.msg.ppap.exchange.adapter.in.rest;

import com.msg.ppap.exchange.config.EdcProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Shared secret check of {@code /internal/edc/**}: the own data plane adds the secret as header (DataAddress {@code
 * authKey}/{@code secretName}); compared in constant time; never logged.
 */
@Component
class CallbackAuthentication {

    private static final Logger LOG = LoggerFactory.getLogger(CallbackAuthentication.class);

    /** Consumer BPN of the contract agreement, added by the Tractus-X provider data plane. */
    static final String EDC_BPN_HEADER = "Edc-Bpn";

    /** Contract agreement id, added by the Tractus-X provider data plane. */
    static final String EDC_AGREEMENT_HEADER = "Edc-Contract-Agreement-Id";

    private final EdcProperties properties;

    CallbackAuthentication(EdcProperties properties) {
        this.properties = properties;
    }

    boolean authorized(HttpServletRequest request) {
        if (!properties.callbackSecretConfigured()) {
            return false;
        }
        String provided = request.getHeader(properties.callbackSecretHeader());
        boolean ok = provided != null
                && MessageDigest.isEqual(
                        provided.getBytes(StandardCharsets.UTF_8),
                        properties.callbackSecret().getBytes(StandardCharsets.UTF_8));
        if (!ok) {
            // header names only – never log secret values
            LOG.warn(
                    "Rejected {} {}: {} header {} (headers: {})",
                    request.getMethod(),
                    request.getRequestURI(),
                    properties.callbackSecretHeader(),
                    provided == null ? "missing" : "does not match",
                    java.util.Collections.list(request.getHeaderNames()));
        }
        return ok;
    }
}
