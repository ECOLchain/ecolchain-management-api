package com.ecolchain.api.documents.adapter.out.storage;

import com.ecolchain.api.DocumentStorageService;
import com.ecolchain.api.documents.application.port.out.ObjectStorage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import java.time.Duration;

/** Adapta o DocumentStorageService (presign OCI S3-compat) à porta ObjectStorage. */
@ApplicationScoped
public class S3ObjectStorage implements ObjectStorage {

    @Inject DocumentStorageService storage;
    @Inject S3Client s3;
    @Inject com.ecolchain.api.DocumentsConfig cfg;

    @Override
    public Presigned presignUpload(String objectKeyHint, String contentType, long sizeBytes) {
        var p = storage.presignUpload(objectKeyHint, contentType, sizeBytes);
        return new Presigned(p.url(), p.key(), Duration.between(java.time.Instant.now(), p.expiresAt()).toSeconds(), p.contentType());
    }

    @Override
    public Presigned presignDownload(String objectKey) {
        var p = storage.presignDownload(objectKey);
        return new Presigned(p.url(), p.key(), Duration.between(java.time.Instant.now(), p.expiresAt()).toSeconds(), null);
    }

    @Override
    public boolean exists(String objectKey) {
        try {
            s3.headObject(HeadObjectRequest.builder().bucket(cfg.bucket()).key(objectKey).build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (software.amazon.awssdk.services.s3.model.S3Exception e) {
            if (e.statusCode() == 404) return false;
            throw e;
        }
    }
}
