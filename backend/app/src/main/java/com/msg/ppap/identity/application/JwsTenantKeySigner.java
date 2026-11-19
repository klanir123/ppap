package com.msg.ppap.identity.application;

import com.msg.ppap.identity.PayloadSignature;
import com.msg.ppap.identity.PayloadSigner;
import com.msg.ppap.identity.SignatureVerifier;
import com.msg.ppap.identity.application.port.out.SigningKeyProvider;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import java.nio.charset.StandardCharsets;
import java.security.interfaces.ECPublicKey;
import java.text.ParseException;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Signs and verifies exchange payloads with the tenant key (ADR-0008).
 *
 * <p><b>JWS variant:</b> ES256, detached compact serialization with <em>unencoded payload</em> per RFC 7797:
 * protected header {@code {"alg":"ES256","b64":false,"crit":["b64"],"kid":"<RFC 7638 thumbprint>"}}, serialized as
 * {@code <base64url(header)>..<base64url(signature)>}. The signing input is
 * {@code ASCII(base64url(header)) || '.' || canonical payload bytes}, i.e. the signature covers exactly the stored
 * and transferred RFC 8785 bytes without a second (base64url) encoding of the payload. Verifiers need the payload
 * bytes and must understand {@code b64} (listed in {@code crit}).
 *
 * <p>The key is loaded once on startup through {@link SigningKeyProvider}; a failure aborts the startup.
 */
@Service
public class JwsTenantKeySigner implements PayloadSigner, SignatureVerifier {

    /** Signature algorithm. */
    public static final String ALG = "ES256";

    private static final Logger LOG = LoggerFactory.getLogger(JwsTenantKeySigner.class);
    private static final Set<String> DEFERRED_CRITICAL = Set.of("b64");

    private final ECKey key;
    private final String kid;
    private final ECDSASigner signer;
    private final ECDSAVerifier verifier;

    public JwsTenantKeySigner(SigningKeyProvider provider) {
        SigningKeyProvider.SigningKey loaded = provider.load();
        try {
            ECKey publicOnly = new ECKey.Builder(Curve.P_256, loaded.publicKey()).build();
            this.kid = publicOnly.computeThumbprint().toString();
            this.key = new ECKey.Builder(Curve.P_256, loaded.publicKey())
                    .privateKey(loaded.privateKey())
                    .keyID(kid)
                    .keyUse(KeyUse.SIGNATURE)
                    .algorithm(JWSAlgorithm.ES256)
                    .build();
            this.signer = new ECDSASigner(loaded.privateKey());
            this.verifier = new ECDSAVerifier(loaded.publicKey(), DEFERRED_CRITICAL);
        } catch (JOSEException ex) {
            throw new IllegalStateException("Signing key from " + provider.description() + " is not usable", ex);
        }
        // Self test: a key pair that does not match fails here and not on the first decision.
        byte[] probe = "ppap-signing-self-test".getBytes(StandardCharsets.UTF_8);
        if (verify(probe, sign(probe).jws()) != Result.VALID) {
            throw new IllegalStateException(
                    "Signing key from " + provider.description() + " failed the self test (key pair mismatch)");
        }
        LOG.info("Payload signing key loaded from {} (kid {})", provider.description(), kid);
    }

    @Override
    public PayloadSignature sign(byte[] canonicalPayload) {
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.ES256)
                .keyID(kid)
                .base64URLEncodePayload(false)
                .criticalParams(DEFERRED_CRITICAL)
                .build();
        JWSObject jws = new JWSObject(header, new Payload(canonicalPayload));
        try {
            jws.sign(signer);
        } catch (JOSEException ex) {
            throw new IllegalStateException("Payload signing failed", ex);
        }
        return new PayloadSignature(ALG, kid, jws.serialize(true));
    }

    @Override
    public Result verify(byte[] canonicalPayload, String jws) {
        if (jws == null || jws.isBlank()) {
            return Result.INVALID;
        }
        try {
            JWSObject parsed = JWSObject.parse(jws, new Payload(canonicalPayload));
            JWSHeader header = parsed.getHeader();
            if (!JWSAlgorithm.ES256.equals(header.getAlgorithm()) || header.isBase64URLEncodePayload()) {
                return Result.INVALID;
            }
            if (!kid.equals(header.getKeyID())) {
                // TODO(phase-4): resolve the partner's public key (DID document / JWKS) by kid.
                return Result.UNKNOWN_KEY;
            }
            return parsed.verify(verifier) ? Result.VALID : Result.INVALID;
        } catch (ParseException | JOSEException | IllegalStateException ex) {
            return Result.INVALID;
        }
    }

    @Override
    public String keyId(String jws) {
        try {
            return JWSObject.parse(jws, new Payload(new byte[0])).getHeader().getKeyID();
        } catch (ParseException | RuntimeException ex) {
            return null;
        }
    }

    /** Key id of the tenant key. */
    public String kid() {
        return kid;
    }

    /** Public tenant key. */
    public ECPublicKey publicKey() {
        try {
            return key.toECPublicKey();
        } catch (JOSEException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** Public JWK set ({@code {"keys":[…]}}) of the tenant. */
    public Map<String, Object> publicJwkSet() {
        return new JWKSet(key.toPublicJWK()).toJSONObject(true);
    }
}
