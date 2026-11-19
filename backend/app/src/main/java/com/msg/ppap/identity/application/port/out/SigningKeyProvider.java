package com.msg.ppap.identity.application.port.out;

import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;

/**
 * Source of the tenant signing key (EC P-256): OpenBao/Vault KV, a PEM file or – for tests and development only – a
 * generated key. Called once on startup; failures abort the startup (signing is essential).
 */
public interface SigningKeyProvider {

    /**
     * Loads the key pair.
     *
     * @throws IllegalStateException with a clear message if the key cannot be loaded or is not an EC P-256 key
     */
    SigningKey load();

    /** Human-readable description of the source for logs, e.g. {@code vault:secret/ppap-signing-key}. */
    String description();

    /** Loaded key pair. */
    record SigningKey(ECPrivateKey privateKey, ECPublicKey publicKey) {}
}
