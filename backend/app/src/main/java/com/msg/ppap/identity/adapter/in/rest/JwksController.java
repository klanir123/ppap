package com.msg.ppap.identity.adapter.in.rest;

import com.msg.ppap.identity.application.JwsTenantKeySigner;
import java.time.Duration;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public JWK set of the tenant ({@code GET /.well-known/jwks.json}): verification key of payload signatures
 * (ADR-0008). Later additionally published as verification method in the DID document.
 */
@RestController
public class JwksController {

    /** Path of the JWK set. */
    public static final String PATH = "/.well-known/jwks.json";

    static final MediaType JWK_SET = MediaType.parseMediaType("application/jwk-set+json");

    private final JwsTenantKeySigner signer;

    public JwksController(JwsTenantKeySigner signer) {
        this.signer = signer;
    }

    @GetMapping(
            path = PATH,
            produces = {"application/jwk-set+json", MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<Map<String, Object>> jwks() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .contentType(JWK_SET)
                .body(signer.publicJwkSet());
    }
}
