package com.ecolchain.api.documents.application.port.out;

public interface ObjectStorage {
    record Presigned(String url, String objectKey, long expiresInSeconds, String contentType) {}
    Presigned presignUpload(String objectKey, String contentType, long sizeBytes);
    Presigned presignDownload(String objectKey);
    boolean exists(String objectKey);
}
