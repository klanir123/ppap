package com.msg.ppap.exchange.application.port.out;

/**
 * Port to the participant's dataspace connector (ADR-0001). All data exchange with partners goes through this port;
 * no direct HTTP calls between participant backends.
 *
 * <p>Phase 1 only covers health; asset/policy/contract provisioning, catalog, negotiation and transfer follow in
 * phase 4.
 */
public interface DataspaceConnector {

    /** Checks whether the connector's control plane management API is reachable and accepts our credentials. */
    ConnectorHealth health();
}
