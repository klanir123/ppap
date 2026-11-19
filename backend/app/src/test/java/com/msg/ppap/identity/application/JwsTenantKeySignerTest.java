package com.msg.ppap.identity.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.msg.ppap.identity.SignatureVerifier;
import com.msg.ppap.identity.application.port.out.SigningKeyProvider;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JwsTenantKeySignerTest {

    static SigningKeyProvider generated() {
        return new SigningKeyProvider() {
            @Override
            public SigningKey load() {
                try {
                    var generator = KeyPairGenerator.getInstance("EC");
                    generator.initialize(new ECGenParameterSpec("secp256r1"));
                    var pair = generator.generateKeyPair();
                    return new SigningKey((ECPrivateKey) pair.getPrivate(), (ECPublicKey) pair.getPublic());
                } catch (Exception ex) {
                    throw new IllegalStateException(ex);
                }
            }

            @Override
            public String description() {
                return "test";
            }
        };
    }

    @Test
    void signsDetachedUnencodedPayload() throws Exception {
        var signer = new JwsTenantKeySigner(generated());
        byte[] payload = "{\"a\":1}".getBytes(StandardCharsets.UTF_8);
        var signature = signer.sign(payload);
        assertThat(signature.alg()).isEqualTo("ES256");
        String[] parts = signature.jws().split("\\.", -1);
        assertThat(parts).hasSize(3);
        assertThat(parts[1]).isEmpty();
        String header = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
        assertThat(header)
                .contains("\"b64\":false")
                .contains("\"crit\":[\"b64\"]")
                .contains(signer.kid());
        assertThat(signature.kid())
                .isEqualTo(new ECKey.Builder(Curve.P_256, signer.publicKey())
                        .build()
                        .computeThumbprint()
                        .toString());
        assertThat(signer.keyId(signature.jws())).isEqualTo(signer.kid());

        assertThat(signer.verify(payload, signature.jws())).isEqualTo(SignatureVerifier.Result.VALID);
        assertThat(signer.verify("{\"a\":2}".getBytes(StandardCharsets.UTF_8), signature.jws()))
                .isEqualTo(SignatureVerifier.Result.INVALID);
        assertThat(signer.verify(payload, "garbage")).isEqualTo(SignatureVerifier.Result.INVALID);
        assertThat(signer.verify(payload, null)).isEqualTo(SignatureVerifier.Result.INVALID);
        assertThat(signer.keyId("garbage")).isNull();

        var other = new JwsTenantKeySigner(generated());
        assertThat(signer.verify(payload, other.sign(payload).jws())).isEqualTo(SignatureVerifier.Result.UNKNOWN_KEY);

        Map<String, Object> jwks = signer.publicJwkSet();
        @SuppressWarnings("unchecked")
        var keys = (List<Map<String, Object>>) jwks.get("keys");
        assertThat(keys).hasSize(1);
        assertThat(keys.getFirst()).containsEntry("kid", signer.kid()).containsEntry("crv", "P-256");
        assertThat(keys.getFirst()).doesNotContainKey("d");
    }
}
