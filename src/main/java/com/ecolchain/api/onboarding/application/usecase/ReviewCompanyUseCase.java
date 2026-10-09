package com.ecolchain.api.onboarding.application.usecase;

import com.ecolchain.api.common.web.BusinessException;
import com.ecolchain.api.common.web.ErrorCodes;
import com.ecolchain.api.identity.domain.Account;
import com.ecolchain.api.onboarding.application.port.out.AuditStore;
import com.ecolchain.api.onboarding.application.port.out.CompanyStore;
import com.ecolchain.api.onboarding.domain.Company;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.UUID;

/** Revisão manual admin: APPROVE / REQUEST_CHANGES / REJECT / SUSPEND. docs/features/revisao-manual.md */
@ApplicationScoped
public class ReviewCompanyUseCase {

    public enum Decision { APPROVE, REQUEST_CHANGES, REJECT, SUSPEND }

    @Inject CompanyStore companies;
    @Inject AuditStore audit;

    @Transactional
    public Company execute(Account admin, UUID companyId, Decision decision, String note) {
        var company = companies.byId(companyId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.COMPANY_NOT_FOUND, 404, "empresa não encontrada"));
        switch (decision) {
            case APPROVE -> {
                if (company.status != Company.Status.UNDER_REVIEW) {
                    throw new BusinessException(ErrorCodes.REVIEW_INVALID_STATE, 409,
                            "só é possível aprovar empresa em análise");
                }
                company.status = Company.Status.APPROVED;
                company.reviewNotes = null;
            }
            case REQUEST_CHANGES -> {
                if (company.status != Company.Status.UNDER_REVIEW) {
                    throw new BusinessException(ErrorCodes.REVIEW_INVALID_STATE, 409,
                            "só é possível pedir ajustes em empresa em análise");
                }
                if (note == null || note.isBlank()) {
                    throw new BusinessException(ErrorCodes.REVIEW_NOTE_REQUIRED, 400,
                            "nota obrigatória ao pedir ajustes");
                }
                company.status = Company.Status.CHANGES_REQUESTED;
                company.reviewNotes = note;
            }
            case REJECT -> {
                if (company.status != Company.Status.UNDER_REVIEW
                        && company.status != Company.Status.CHANGES_REQUESTED) {
                    throw new BusinessException(ErrorCodes.REVIEW_INVALID_STATE, 409,
                            "só é possível rejeitar empresa em análise ou com ajustes pendentes");
                }
                if (note == null || note.isBlank()) {
                    throw new BusinessException(ErrorCodes.REVIEW_NOTE_REQUIRED, 400,
                            "nota obrigatória ao rejeitar");
                }
                company.status = Company.Status.REJECTED;
                company.reviewNotes = note;
            }
            case SUSPEND -> {
                if (company.status != Company.Status.APPROVED) {
                    throw new BusinessException(ErrorCodes.REVIEW_INVALID_STATE, 409,
                            "só é possível suspender empresa aprovada");
                }
                company.status = Company.Status.SUSPENDED;
                company.reviewNotes = note;
            }
        }
        companies.save(company);
        audit.log(admin.id, admin.email, "COMPANY_REVIEW_" + decision, "company",
                company.id.toString(), note);
        return company;
    }
}
