package com.msg.ppap.support;

import java.time.Duration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Containers shared by all integration tests (same versions as docker compose, docs/versions.md): PostgreSQL and
 * SeaweedFS as S3-compatible object storage (bucket created on startup).
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    public static final DockerImageName POSTGRES_IMAGE = DockerImageName.parse("postgres:18.7");

    public static final DockerImageName SEAWEEDFS_IMAGE = DockerImageName.parse("chrislusf/seaweedfs:4.54");

    static final int S3_PORT = 8333;

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(POSTGRES_IMAGE);
    }

    @Bean
    GenericContainer<?> seaweedfsContainer() {
        return new GenericContainer<>(SEAWEEDFS_IMAGE)
                .withCommand("server", "-s3", "-dir=/data", "-volume.max=10")
                .withExposedPorts(S3_PORT)
                .waitingFor(Wait.forHttp("/")
                        .forPort(S3_PORT)
                        .forStatusCodeMatching(code -> code < 500)
                        .withStartupTimeout(Duration.ofMinutes(2)));
    }

    @Bean
    DynamicPropertyRegistrar objectStorageProperties(GenericContainer<?> seaweedfsContainer) {
        return registry -> {
            registry.add(
                    "ppap.storage.endpoint",
                    () -> "http://" + seaweedfsContainer.getHost() + ":" + seaweedfsContainer.getMappedPort(S3_PORT));
            registry.add("ppap.storage.create-bucket", () -> "true");
        };
    }
}
