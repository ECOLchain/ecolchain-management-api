package com.ecolchain.api.onboarding.application.port.out;

import com.ecolchain.api.onboarding.domain.Company;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompanyStore {
    Optional<Company> byId(UUID id);
    Optional<Company> byMemberAccountId(UUID accountId);
    Optional<Company> byCnpj(String cnpj);
    Company save(Company company);
    void setProfiles(UUID companyId, List<UUID> profileTypeIds);
    void addOwnerMember(UUID companyId, UUID accountId);
    List<Company> listByStatus(Company.Status status, int page, int size);
    long countByStatus(Company.Status status);
    List<UUID> memberAccountIds(UUID companyId);
}
