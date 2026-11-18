package com.msg.ppap.documents.config;

import jakarta.validation.constraints.NotBlank;
import java.net.URI;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * S3-compatible object storage settings (ADR-0007; SeaweedFS locally).
 *
 * @param endpoint S3 endpoint; empty uses AWS defaults ({@code PPAP_S3_ENDPOINT})
 * @param region signing region ({@code PPAP_S3_REGION})
 * @param accessKey access key; empty uses the default AWS credentials chain ({@code PPAP_S3_ACCESS_KEY})
 * @param secretKey secret key ({@code PPAP_S3_SECRET_KEY})
 * @param bucket bucket of this tenant ({@code PPAP_S3_BUCKET})
 * @param pathStyle use path-style access, required by most S3-compatible stores ({@code PPAP_S3_PATH_STYLE})
 * @param createBucket create the bucket on startup if missing ({@code PPAP_S3_CREATE_BUCKET})
 */
@Validated
@ConfigurationProperties("ppap.storage")
public record StorageProperties(
        @Nullable URI endpoint,
        @DefaultValue("us-east-1") String region,
        @Nullable String accessKey,
        @Nullable String secretKey,
        @NotBlank String bucket,
        @DefaultValue("true") boolean pathStyle,
        @DefaultValue("false") boolean createBucket) {}
