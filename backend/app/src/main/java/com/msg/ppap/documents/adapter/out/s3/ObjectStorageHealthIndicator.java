package com.msg.ppap.documents.adapter.out.s3;

import com.msg.ppap.documents.config.StorageProperties;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;

/** Actuator health indicator {@code objectStorage}: checks that the tenant bucket is reachable (HeadBucket). */
@Component("objectStorageHealthIndicator")
class ObjectStorageHealthIndicator implements HealthIndicator {

    private final S3DocumentStorage storage;
    private final StorageProperties properties;

    ObjectStorageHealthIndicator(S3DocumentStorage storage, StorageProperties properties) {
        this.storage = storage;
        this.properties = properties;
    }

    @Override
    public Health health() {
        Health.Builder builder = Health.unknown().withDetail("bucket", properties.bucket());
        if (properties.endpoint() != null) {
            builder.withDetail("endpoint", properties.endpoint().toString());
        }
        try {
            storage.headBucket();
            return builder.up().build();
        } catch (SdkException ex) {
            return builder.down()
                    .withDetail("error", ex.getClass().getSimpleName() + ": " + ex.getMessage())
                    .build();
        }
    }
}
