package com.msg.ppap.documents.adapter.out.s3;

import com.msg.ppap.documents.application.port.out.DocumentStorage;
import com.msg.ppap.documents.application.port.out.DocumentStorageException;
import com.msg.ppap.documents.application.port.out.StoredDocument;
import com.msg.ppap.documents.config.StorageProperties;
import java.io.InputStream;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

/** {@link DocumentStorage} backed by an S3-compatible object store (one bucket per tenant). */
@Component
public class S3DocumentStorage implements DocumentStorage {

    private static final Logger LOG = LoggerFactory.getLogger(S3DocumentStorage.class);

    private final ObjectProvider<S3Client> s3;
    private final StorageProperties properties;

    public S3DocumentStorage(ObjectProvider<S3Client> s3, StorageProperties properties) {
        this.s3 = s3;
        this.properties = properties;
    }

    @Override
    public void put(String key, InputStream content, long contentLength, String contentType) {
        try {
            s3.getObject()
                    .putObject(
                            request -> request.bucket(properties.bucket())
                                    .key(key)
                                    .contentType(contentType)
                                    .contentLength(contentLength),
                            RequestBody.fromInputStream(content, contentLength));
        } catch (SdkException ex) {
            throw new DocumentStorageException("Failed to store object '" + key + "'", ex);
        }
    }

    @Override
    public Optional<StoredDocument> get(String key) {
        try {
            ResponseInputStream<GetObjectResponse> stream = s3.getObject()
                    .getObject(request -> request.bucket(properties.bucket()).key(key));
            GetObjectResponse response = stream.response();
            return Optional.of(new StoredDocument(stream, response.contentLength(), response.contentType()));
        } catch (NoSuchKeyException ex) {
            return Optional.empty();
        } catch (SdkException ex) {
            throw new DocumentStorageException("Failed to load object '" + key + "'", ex);
        }
    }

    @Override
    public boolean exists(String key) {
        try {
            s3.getObject()
                    .headObject(request -> request.bucket(properties.bucket()).key(key));
            return true;
        } catch (NoSuchKeyException ex) {
            return false;
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                return false;
            }
            throw new DocumentStorageException("Failed to check object '" + key + "'", ex);
        } catch (SdkException ex) {
            throw new DocumentStorageException("Failed to check object '" + key + "'", ex);
        }
    }

    /** Checks that the tenant bucket is reachable; used by the {@code objectStorage} health indicator. */
    void headBucket() {
        s3.getObject().headBucket(request -> request.bucket(properties.bucket()));
    }

    /** Creates the bucket on startup if enabled. Failures are logged, never fatal. */
    @EventListener(ApplicationReadyEvent.class)
    void ensureBucketOnStartup() {
        if (!properties.createBucket()) {
            return;
        }
        try {
            ensureBucket();
        } catch (SdkException ex) {
            LOG.warn("Could not ensure bucket '{}' on startup: {}", properties.bucket(), ex.getMessage());
        }
    }

    void ensureBucket() {
        S3Client client = s3.getObject();
        try {
            client.headBucket(request -> request.bucket(properties.bucket()));
            LOG.debug("Bucket '{}' exists", properties.bucket());
        } catch (NoSuchBucketException ex) {
            createBucket(client);
        } catch (S3Exception ex) {
            if (ex.statusCode() != 404) {
                throw ex;
            }
            createBucket(client);
        }
    }

    private void createBucket(S3Client client) {
        client.createBucket(request -> request.bucket(properties.bucket()));
        LOG.info("Created bucket '{}'", properties.bucket());
    }
}
