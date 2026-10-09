package com.ecolchain.api.documents.adapter.out.persistence;

import com.ecolchain.api.documents.application.port.out.DocumentStore;
import com.ecolchain.api.documents.domain.CompanyDocument;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class PanacheDocumentStore implements DocumentStore {

    @Override
    public Optional<CompanyDocument> byCompanyAndAttribute(UUID companyId, UUID attributeId) {
        return DocumentEntity.<DocumentEntity>find("companyId = ?1 and attributeId = ?2", companyId, attributeId)
                .firstResultOptional().map(this::toDomain);
    }

    @Override
    @Transactional
    public CompanyDocument save(CompanyDocument d) {
        DocumentEntity e = d.id != null ? DocumentEntity.findById(d.id) : null;
        if (e == null) {
            e = new DocumentEntity();
            e.id = d.id == null ? UUID.randomUUID() : d.id;
            e.createdAt = Instant.now();
        }
        e.companyId = d.companyId; e.attributeId = d.attributeId;
        e.objectKey = d.objectKey; e.fileName = d.fileName;
        e.contentType = d.contentType; e.sizeBytes = d.sizeBytes;
        e.status = d.status.name(); e.reviewNote = d.reviewNote; e.uploadedAt = d.uploadedAt;
        e.persist();
        d.id = e.id;
        return d;
    }

    @Override
    public List<CompanyDocument> byCompany(UUID companyId) {
        return DocumentEntity.<DocumentEntity>find("companyId", companyId).list()
                .stream().map(this::toDomain).toList();
    }

    private CompanyDocument toDomain(DocumentEntity e) {
        var d = new CompanyDocument();
        d.id = e.id; d.companyId = e.companyId; d.attributeId = e.attributeId;
        d.objectKey = e.objectKey; d.fileName = e.fileName; d.contentType = e.contentType;
        d.sizeBytes = e.sizeBytes; d.status = CompanyDocument.Status.valueOf(e.status);
        d.reviewNote = e.reviewNote; d.uploadedAt = e.uploadedAt; d.createdAt = e.createdAt;
        return d;
    }
}
