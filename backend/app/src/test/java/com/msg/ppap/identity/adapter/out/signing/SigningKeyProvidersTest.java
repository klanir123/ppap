package com.msg.ppap.identity.adapter.out.signing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.msg.ppap.identity.application.JwsTenantKeySigner;
import com.msg.ppap.identity.config.SigningProperties;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.json.JsonMapper;

class SigningKeyProvidersTest {

    static final DockerImageName OPENBAO = DockerImageName.parse("openbao/openbao:2.7.2");

    @TempDir
    Path dir;

    static KeyPair keyPair(String curve) throws Exception {
        var generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec(curve));
        return generator.generateKeyPair();
    }

    static String pem(KeyPair pair) {
        return "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes())
                        .encodeToString(pair.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
    }

    @Test
    void derivesPublicKeyFromPkcs8Pem() throws Exception {
        KeyPair pair = keyPair("secp256r1");
        var privateKey = EcKeys.parsePkcs8Pem(pem(pair));
        assertThat(EcKeys.publicKeyOf(privateKey)).isEqualTo(pair.getPublic());
        assertThatThrownBy(() -> EcKeys.parsePkcs8Pem("garbage")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EcKeys.parsePkcs8Pem(pem(keyPair("secp384r1"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fileProviderLoadsKeyAndFailsFast() throws Exception {
        KeyPair pair = keyPair("secp256r1");
        Path file = Files.writeString(dir.resolve("key.pem"), pem(pair));
        var provider = new FileSigningKeyProvider(file);
        assertThat(provider.load().publicKey()).isEqualTo(pair.getPublic());
        assertThat(provider.description()).startsWith("file:");
        assertThatIllegalStateException()
                .isThrownBy(() -> new FileSigningKeyProvider(null).load())
                .withMessageContaining("PPAP_SIGNING_KEY_FILE");
        assertThatIllegalStateException()
                .isThrownBy(() -> new FileSigningKeyProvider(dir.resolve("missing.pem")).load());
        Path broken = Files.writeString(dir.resolve("broken.pem"), "nope");
        assertThatIllegalStateException().isThrownBy(() -> new FileSigningKeyProvider(broken).load());

        var signer = new JwsTenantKeySigner(provider);
        assertThat(signer.kid()).hasSize(43);
        assertThat(signer.publicKey()).isEqualTo(pair.getPublic());
    }

    @Test
    void vaultProviderFailsFastWithoutConfigurationOrServer() {
        var unconfigured =
                new SigningProperties.Vault(null, null, "secret", "ppap-signing-key", "content", Duration.ofSeconds(1));
        assertThatIllegalStateException()
                .isThrownBy(() -> new VaultSigningKeyProvider(
                                unconfigured, JsonMapper.builder().build())
                        .load())
                .withMessageContaining("PPAP_VAULT_URL");
        var unreachable = new SigningProperties.Vault(
                URI.create("http://127.0.0.1:1"), "t", "secret", "ppap-signing-key", "content", Duration.ofSeconds(1));
        var provider =
                new VaultSigningKeyProvider(unreachable, JsonMapper.builder().build());
        assertThat(provider.secretUri()).hasToString("http://127.0.0.1:1/v1/secret/data/ppap-signing-key");
        assertThatIllegalStateException().isThrownBy(provider::load).withMessageContaining("cannot be read");
    }

    @Test
    void configurationSelectsProvider() {
        var config = new SigningKeyConfiguration();
        var vault = new SigningProperties.Vault(null, null, "secret", "k", "content", Duration.ofSeconds(1));
        JsonMapper mapper = JsonMapper.builder().build();
        assertThat(config.signingKeyProvider(
                        new SigningProperties(SigningProperties.Provider.GENERATED, null, vault), mapper))
                .isInstanceOf(GeneratedSigningKeyProvider.class);
        assertThat(config.signingKeyProvider(
                        new SigningProperties(SigningProperties.Provider.FILE, null, vault), mapper))
                .isInstanceOf(FileSigningKeyProvider.class);
        assertThat(config.signingKeyProvider(
                        new SigningProperties(SigningProperties.Provider.VAULT, null, vault), mapper))
                .isInstanceOf(VaultSigningKeyProvider.class);
        assertThat(new GeneratedSigningKeyProvider().description()).contains("generated");
    }

    @Test
    void readsKeyFromOpenBaoKvV2() throws Exception {
        try (var bao = new GenericContainer<>(OPENBAO)
                .withCommand("server", "-dev", "-dev-root-token-id=root", "-dev-listen-address=0.0.0.0:8200")
                .withExposedPorts(8200)
                .waitingFor(Wait.forHttp("/v1/sys/health").forPort(8200).forStatusCode(200))) {
            bao.start();
            String url = "http://" + bao.getHost() + ":" + bao.getMappedPort(8200);
            KeyPair pair = keyPair("secp256r1");
            String body = JsonMapper.builder()
                    .build()
                    .writeValueAsString(java.util.Map.of("data", java.util.Map.of("content", pem(pair))));
            try (HttpClient client = HttpClient.newHttpClient()) {
                var response = client.send(
                        HttpRequest.newBuilder(URI.create(url + "/v1/secret/data/ppap-signing-key"))
                                .header("X-Vault-Token", "root")
                                .POST(HttpRequest.BodyPublishers.ofString(body))
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
                assertThat(response.statusCode()).isEqualTo(200);
            }
            var vault = new SigningProperties.Vault(
                    URI.create(url + "/"), "root", "secret", "ppap-signing-key", "content", Duration.ofSeconds(5));
            var provider =
                    new VaultSigningKeyProvider(vault, JsonMapper.builder().build());
            assertThat(provider.load().publicKey()).isEqualTo(pair.getPublic());
            assertThat(provider.description()).isEqualTo("vault:secret/ppap-signing-key");

            var wrongToken = new SigningProperties.Vault(
                    URI.create(url), "wrong", "secret", "ppap-signing-key", "content", Duration.ofSeconds(5));
            assertThatIllegalStateException()
                    .isThrownBy(() -> new VaultSigningKeyProvider(
                                    wrongToken, JsonMapper.builder().build())
                            .load())
                    .withMessageContaining("HTTP 403");
            var wrongField = new SigningProperties.Vault(
                    URI.create(url), "root", "secret", "ppap-signing-key", "pem", Duration.ofSeconds(5));
            assertThatIllegalStateException()
                    .isThrownBy(() -> new VaultSigningKeyProvider(
                                    wrongField, JsonMapper.builder().build())
                            .load())
                    .withMessageContaining("no field 'pem'");
        }
    }
}
