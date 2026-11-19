package com.msg.ppap.identity.adapter.out.signing;

import com.msg.ppap.identity.application.port.out.SigningKeyProvider;
import java.security.GeneralSecurityException;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generates a new in-memory P-256 key on every start. For tests and local development only: signatures become
 * unverifiable after a restart.
 */
class GeneratedSigningKeyProvider implements SigningKeyProvider {

    private static final Logger LOG = LoggerFactory.getLogger(GeneratedSigningKeyProvider.class);

    @Override
    public SigningKey load() {
        LOG.warn("Using a GENERATED in-memory signing key (ppap.signing.provider=generated): for development and "
                + "tests only, signatures cannot be verified after a restart");
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            var pair = generator.generateKeyPair();
            return new SigningKey((ECPrivateKey) pair.getPrivate(), (ECPublicKey) pair.getPublic());
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Cannot generate signing key", ex);
        }
    }

    @Override
    public String description() {
        return "generated (in-memory)";
    }
}
