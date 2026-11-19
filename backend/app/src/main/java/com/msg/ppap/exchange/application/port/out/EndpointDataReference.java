package com.msg.ppap.exchange.application.port.out;

import java.net.URI;
import java.util.Objects;

/**
 * Access to the partner's data plane for one asset.
 *
 * @param transferProcessId transfer process id of the EDR
 * @param agreementId contract agreement id
 * @param endpoint public data plane endpoint
 * @param authorization token for the {@code Authorization} header
 * @param cached whether an existing EDR was reused
 */
public record EndpointDataReference(
        String transferProcessId, String agreementId, URI endpoint, String authorization, boolean cached) {

    public EndpointDataReference {
        Objects.requireNonNull(endpoint, "endpoint");
        Objects.requireNonNull(authorization, "authorization");
    }

    @Override
    public String toString() {
        return "EndpointDataReference[" + transferProcessId + ", " + endpoint + ", cached=" + cached + "]";
    }
}
