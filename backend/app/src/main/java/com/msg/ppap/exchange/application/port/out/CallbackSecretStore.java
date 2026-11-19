package com.msg.ppap.exchange.application.port.out;

/**
 * Secret store of the participant that the EDC data plane reads (HashiCorp Vault API / OpenBao). The callback secret
 * is stored there so that it never appears in plain text in the EDC asset (DataAddress {@code secretName}).
 */
public interface CallbackSecretStore {

    /** Whether a secret store is configured. */
    boolean available();

    /**
     * Writes the secret under the given name (idempotent).
     *
     * @throws RuntimeException if the store is not reachable
     */
    void store(String name, String value);
}
