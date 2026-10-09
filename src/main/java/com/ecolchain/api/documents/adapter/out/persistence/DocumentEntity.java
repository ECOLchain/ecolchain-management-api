package com.ecolchain.api.documents.adapter.out.persistence;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "document")
public class DocumentEntity extends PanacheEntityBase {
    @Id public UUID id;
    @Column(name = "company_id", nullable = false) public UUID companyId;
    @Column(name = "attribute_id", nullable = false) public UUID attributeId;
    @Column(name = "object_key", nullable = false) public String objectKey;
    @Column(name = "file_name", nullable = false) public String fileName;
    @Column(name = "content_type", nullable = false) public String contentType;
    @Column(name = "size_bytes", nullable = false) public long sizeBytes;
    @Column(nullable = false) public String status;
    @Column(name = "review_note") public String reviewNote;
    @Column(name = "uploaded_at") public Instant uploadedAt;
    @Column(name = "created_at", nullable = false) public Instant createdAt;
}
