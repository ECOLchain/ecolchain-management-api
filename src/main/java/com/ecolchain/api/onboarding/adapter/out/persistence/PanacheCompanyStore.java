package com.ecolchain.api.onboarding.adapter.out.persistence;

import com.ecolchain.api.catalog.adapter.out.persistence.ProfileTypeEntity;
import com.ecolchain.api.onboarding.application.port.out.CompanyStore;
import com.ecolchain.api.onboarding.domain.Company;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
public class PanacheCompanyStore implements CompanyStore {

    @Override
    public Optional<Company> byId(UUID id) {
        return Optional.ofNullable(CompanyEntity.findById(id)).map(e -> toDomain((CompanyEntity) e));
    }

    @Override
    public Optional<Company> byMemberAccountId(UUID accountId) {
        return CompanyMemberEntity.<CompanyMemberEntity>find("accountId", accountId)
                .firstResultOptional().flatMap(m -> byId(m.companyId));
    }

    @Override
    public Optional<Company> byCnpj(String cnpj) {
        return CompanyEntity.<CompanyEntity>find("cnpj = ?1 and status <> 'REJECTED'", cnpj)
                .firstResultOptional().map(this::toDomain);
    }

    @Override
    @Transactional
    public Company save(Company c) {
        CompanyEntity e = c.id != null ? CompanyEntity.findById(c.id) : null;
        if (e == null) {
            e = new CompanyEntity();
            e.id = c.id == null ? UUID.randomUUID() : c.id;
            e.createdAt = Instant.now();
        }
        e.cnpj = c.cnpj; e.razaoSocial = c.razaoSocial; e.wallet = c.wallet; e.status = c.status.name();
        e.reviewNotes = c.reviewNotes; e.termsVersion = c.termsVersion;
        e.termsAcceptedAt = c.termsAcceptedAt; e.submittedAt = c.submittedAt;
        e.updatedAt = Instant.now();
        e.persist();
        c.id = e.id;
        return c;
    }

    @Override
    @Transactional
    public void setProfiles(UUID companyId, List<UUID> profileTypeIds) {
        CompanyProfileEntity.delete("companyId", companyId);
        for (UUID ptId : profileTypeIds) {
            var p = new CompanyProfileEntity();
            p.companyId = companyId;
            p.profileTypeId = ptId;
            p.persist();
        }
    }

    @Override
    @Transactional
    public void addOwnerMember(UUID companyId, UUID accountId) {
        var m = new CompanyMemberEntity();
        m.id = UUID.randomUUID();
        m.companyId = companyId;
        m.accountId = accountId;
        m.role = "OWNER";
        m.createdAt = Instant.now();
        m.persist();
    }

    @Override
    public List<Company> listByStatus(Company.Status status, int page, int size) {
        var q = status == null
                ? CompanyEntity.<CompanyEntity>find("order by createdAt desc")
                : CompanyEntity.<CompanyEntity>find("status = ?1 order by createdAt desc", status.name());
        return q.page(page, size).list().stream().map(this::toDomain).toList();
    }

    @Override
    public long countByStatus(Company.Status status) {
        return status == null ? CompanyEntity.count() : CompanyEntity.count("status", status.name());
    }

    @Override
    public List<UUID> memberAccountIds(UUID companyId) {
        return CompanyMemberEntity.<CompanyMemberEntity>find("companyId", companyId).list()
                .stream().map(m -> m.accountId).toList();
    }

    private Company toDomain(CompanyEntity e) {
        var c = new Company();
        c.id = e.id; c.cnpj = e.cnpj; c.razaoSocial = e.razaoSocial; c.wallet = e.wallet;
        c.status = Company.Status.valueOf(e.status);
        c.reviewNotes = e.reviewNotes; c.termsVersion = e.termsVersion;
        c.termsAcceptedAt = e.termsAcceptedAt; c.createdAt = e.createdAt;
        c.updatedAt = e.updatedAt; c.submittedAt = e.submittedAt;
        var ptIds = CompanyProfileEntity.<CompanyProfileEntity>find("companyId", e.id).list()
                .stream().map(p -> p.profileTypeId).toList();
        c.profileTypeCodes = ptIds.isEmpty() ? List.of()
                : ProfileTypeEntity.<ProfileTypeEntity>find("id in ?1", ptIds).list()
                        .stream().map(p -> p.code).sorted().toList();
        return c;
    }
}
