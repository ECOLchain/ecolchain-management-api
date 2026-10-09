package com.ecolchain.api;

import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@ApplicationScoped
public class DocumentStorageService {

    private static final Map<String, String> CONTENT_TYPES = Map.of(
            "pdf", "application/pdf",
            "png", "image/png",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "txt", "text/plain",
            "csv", "text/csv",
            "doc", "application/msword",
            "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "xls", "application/vnd.ms-excel",
            "xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private static final Pattern KEY_PATTERN = Pattern.compile("^docs/[0-9a-f-]{36}-[A-Za-z0-9._-]+$");
    private static final int MAX_FILENAME = 100;

    private final S3Presigner presigner;
    private final DocumentsConfig cfg;

    public DocumentStorageService(S3Presigner presigner, DocumentsConfig cfg) {
        this.presigner = presigner;
        this.cfg = cfg;
    }

    public record PresignedUpload(String key, String url, Instant expiresAt, String contentType, long contentLength) {
    }

    public record PresignedDownload(String key, String url, Instant expiresAt) {
    }

    public PresignedUpload presignUpload(String filename, String contentType, long contentLength) {
        String sanitized = sanitize(filename);
        String ext = sanitized.substring(sanitized.lastIndexOf('.') + 1);
        String expectedType = CONTENT_TYPES.get(ext);
        if (contentType == null || !contentType.equals(expectedType)) {
            throw new InvalidDocumentException(
                    "contentType does not match extension ." + ext + " (expected " + expectedType + ")");
        }
        if (contentLength <= 0 || contentLength > cfg.maxSizeBytes()) {
            throw new InvalidDocumentException(
                    "contentLength must be between 1 and " + cfg.maxSizeBytes() + " bytes");
        }
        String key = "docs/" + UUID.randomUUID() + "-" + sanitized;
        var presigned = presigner.presignPutObject(PutObjectPresignRequest.builder()
                .signatureDuration(cfg.uploadUrlTtl())
                .putObjectRequest(PutObjectRequest.builder()
                        .bucket(cfg.bucket())
                        .key(key)
                        .contentType(contentType)
                        .contentLength(contentLength)
                        .build())
                .build());
        return new PresignedUpload(key, presigned.url().toString(), presigned.expiration(), contentType, contentLength);
    }

    public PresignedDownload presignDownload(String key) {
        if (key == null || key.contains("..") || !KEY_PATTERN.matcher(key).matches()) {
            throw new InvalidDocumentException("invalid document key");
        }
        var presigned = presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(cfg.downloadUrlTtl())
                .getObjectRequest(GetObjectRequest.builder()
                        .bucket(cfg.bucket())
                        .key(key)
                        .build())
                .build());
        return new PresignedDownload(key, presigned.url().toString(), presigned.expiration());
    }

    private static String sanitize(String filename) {
        if (filename == null || filename.isBlank()) {
            throw new InvalidDocumentException("filename is required");
        }
        String leaf = filename;
        int cut = Math.max(leaf.lastIndexOf('/'), leaf.lastIndexOf('\\'));
        if (cut >= 0) {
            leaf = leaf.substring(cut + 1);
        }
        String s = leaf.replace(' ', '-')
                .replaceAll("[^A-Za-z0-9._-]", "")
                .replaceAll("-{2,}", "-")
                .replaceAll("\\.{2,}", ".")
                .replaceAll("^[.-]+", "")
                .replaceAll("[.-]+$", "");
        if (s.isEmpty()) {
            throw new InvalidDocumentException("filename has no usable characters");
        }
        int dot = s.lastIndexOf('.');
        if (dot <= 0 || dot == s.length() - 1) {
            throw new InvalidDocumentException("filename must have an extension");
        }
        String base = s.substring(0, dot);
        String ext = s.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!CONTENT_TYPES.containsKey(ext)) {
            throw new InvalidDocumentException("unsupported file extension: ." + ext);
        }
        int maxBase = MAX_FILENAME - ext.length() - 1;
        if (base.length() > maxBase) {
            base = base.substring(0, maxBase).replaceAll("[.-]+$", "");
        }
        return base + "." + ext;
    }
}
