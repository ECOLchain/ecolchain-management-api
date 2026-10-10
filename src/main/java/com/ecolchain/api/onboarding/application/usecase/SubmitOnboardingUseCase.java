package com.ecolchain.api.onboarding.application.usecase;

import com.ecolchain.api.catalog.domain.AttributeDef;
import com.ecolchain.api.common.web.BusinessException;
import com.ecolchain.api.common.web.ErrorCodes;
import com.ecolchain.api.documents.domain.CompanyDocument;
import com.ecolchain.api.identity.domain.Account;
import com.ecolchain.api.onboarding.application.port.out.AuditStore;
import com.ecolchain.api.onboarding.application.port.out.CompanyStore;
import com.ecolchain.api.onboarding.domain.Company;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.stream.Collectors;

/** Submete o onboarding para revisão manual (todos os requeridos preenchidos). docs/features/revisao-manual.md */
@ApplicationScoped
public class SubmitOnboardingUseCase {

    @Inject CompanyStore companies;
    @Inject GetOnboardingUseCase onboarding;
    @Inject AuditStore audit;

    @Transactional
    public Company execute(Account account) {
        var result = onboarding.execute(account);
        var company = result.company();
        if (!company.editable()) {
            throw new BusinessException(ErrorCodes.COMPANY_LOCKED, 409, "cadastro já está em análise");
        }
        if (result.requiredFilled() < result.requiredTotal()) {
            var missing = result.steps().stream()
                    .flatMap(s -> s.attributes().stream())
                    .filter(a -> a.def().active && a.required())
                    .filter(a -> {
                        if (a.def().kind == AttributeDef.Kind.DATA) {
                            var v = result.values().get(a.def().id);
                            return v == null || v.valueText() == null || v.valueText().isBlank();
                        }
                        var d = result.documents().get(a.def().id);
                        return d == null || (d.status != CompanyDocument.Status.UPLOADED
                                && d.status != CompanyDocument.Status.ACCEPTED);
                    })
                    .map(a -> a.def().code)
                    .collect(Collectors.joining(", "));
            throw new BusinessException(ErrorCodes.SUBMIT_INCOMPLETE, 400,
                    "itens obrigatórios pendentes: " + missing);
        }
        company.status = Company.Status.UNDER_REVIEW;
        company.submittedAt = Instant.now();
        companies.save(company);
        audit.log(account.id, account.email, "COMPANY_SUBMITTED", "company", company.id.toString(), null);
        return company;
    }
}
