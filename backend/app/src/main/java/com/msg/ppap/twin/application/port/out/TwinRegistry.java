package com.msg.ppap.twin.application.port.out;

import java.util.Optional;

/** Port to the own Digital Twin Registry (AAS Registry API 3.x). */
public interface TwinRegistry {

    /** Whether a registry is configured at all. */
    boolean configured();

    /**
     * Shell descriptor as stored in the registry (seen as owner).
     *
     * @return JSON, empty if not registered
     * @throws TwinRegistryException if the registry is not reachable or answers with an error
     */
    Optional<byte[]> findShell(String shellId);

    /**
     * Creates or replaces the shell descriptor incl. its submodel descriptors ({@code PUT}, creates if absent).
     *
     * @throws TwinRegistryException if the registry is not reachable or rejects the descriptor
     */
    void putShell(String shellId, byte[] descriptor);

    /** Reachability check (health). */
    boolean reachable();
}
