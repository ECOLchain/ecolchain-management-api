package com.ecolchain.api.documents.application.port.out;

import com.ecolchain.api.documents.domain.CompanyDocument;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentStore {
    Optional<CompanyDocument> byCompanyAndAttribute(UUID companyId, UUID attributeId);
    CompanyDocument save(CompanyDocument doc);
    List<CompanyDocument> byCompany(UUID companyId);
}
