package com.msg.ppap.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Base class for integration tests: full application context, MockMvc, PostgreSQL via Testcontainers and a mocked
 * {@link JwtDecoder} (tokens are injected with spring-security-test's {@code jwt()} post-processor). EDC and object
 * storage point to closed local ports, so their health is DOWN.
 */
@SpringBootTest(
        properties = {
            "ppap.tenant.bpnl=BPNL00000003AYRE",
            "ppap.tenant.name=Test OEM AG",
            "ppap.tenant.role-label=OEM",
            "ppap.oidc.issuer-uri=http://localhost:8180/realms/test",
            "ppap.oidc.jwk-set-uri=http://localhost:1/realms/test/protocol/openid-connect/certs",
            "ppap.edc.management-url=http://127.0.0.1:1/management",
            "ppap.storage.endpoint=http://127.0.0.1:1",
            "ppap.storage.access-key=test",
            "ppap.storage.secret-key=test",
            "ppap.storage.bucket=test"
        })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public abstract class AbstractIntegrationTest {

    @MockitoBean
    protected JwtDecoder jwtDecoder;
}
