package com.ecolchain.api.documents.domain;

import java.time.Instant;
import java.util.UUID;

public class CompanyDocument {
    public enum Status { PENDING_UPLOAD, UPLOADED, REJECTED, ACCEPTED }

    public UUID id;
    public UUID companyId;
    public UUID attributeId;
    public String objectKey;
    public String fileName;
    public String contentType;
    public long sizeBytes;
    public Status status;
    public String reviewNote;
    public Instant uploadedAt;
    public Instant createdAt;
}
