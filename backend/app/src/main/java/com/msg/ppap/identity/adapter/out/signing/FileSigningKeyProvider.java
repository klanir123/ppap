package com.msg.ppap.identity.adapter.out.signing;

import com.msg.ppap.identity.application.port.out.SigningKeyProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Reads the signing key from a PEM file (PKCS#8, EC P-256), {@code PPAP_SIGNING_KEY_FILE}. */
class FileSigningKeyProvider implements SigningKeyProvider {

    private final Path file;

    FileSigningKeyProvider(Path file) {
        this.file = file;
    }

    @Override
    public SigningKey load() {
        if (file == null || file.toString().isBlank()) {
            throw new IllegalStateException(
                    "ppap.signing.provider=file requires PPAP_SIGNING_KEY_FILE (PEM, PKCS#8, EC P-256)");
        }
        try {
            var privateKey = EcKeys.parsePkcs8Pem(Files.readString(file));
            return new SigningKey(privateKey, EcKeys.publicKeyOf(privateKey));
        } catch (IOException | IllegalArgumentException ex) {
            throw new IllegalStateException("Signing key file " + file + " cannot be read: " + ex.getMessage(), ex);
        }
    }

    @Override
    public String description() {
        return "file:" + file;
    }
}
