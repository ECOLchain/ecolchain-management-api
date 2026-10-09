package com.ecolchain.api.onboarding.application.usecase;

import com.ecolchain.api.catalog.application.port.out.CatalogStore;
import com.ecolchain.api.catalog.domain.AttributeDef;
import com.ecolchain.api.common.web.BusinessException;
import com.ecolchain.api.common.web.ErrorCodes;
import com.ecolchain.api.identity.domain.Account;
import com.ecolchain.api.onboarding.application.port.out.AuditStore;
import com.ecolchain.api.onboarding.application.port.out.AttributeValueStore;
import com.ecolchain.api.onboarding.application.port.out.CompanyStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.regex.Pattern;

/** PUT /me/onboarding/attributes/{code} — salva valor DATA se o atributo está no fluxo. docs/features/onboarding-dinamico.md */
@ApplicationScoped
public class PutAttributeValueUseCase {

    @Inject CompanyStore companies;
    @Inject CatalogStore catalog;
    @Inject AttributeValueStore values;
    @Inject AuditStore audit;
    @Inject GetOnboardingUseCase onboarding;

    @Transactional
    public void execute(Account account, String code, String value) {
        var company = companies.byMemberAccountId(account.id)
                .orElseThrow(() -> new BusinessException(ErrorCodes.COMPANY_NOT_FOUND, 404, "empresa não cadastrada"));
        if (!company.editable()) {
            throw new BusinessException(ErrorCodes.COMPANY_LOCKED, 409, "cadastro em análise não pode ser alterado");
        }
        var def = catalog.attributeByCode(code)
                .orElseThrow(() -> new BusinessException(ErrorCodes.ATTRIBUTE_INVALID, 404, "atributo desconhecido"));
        if (def.kind != AttributeDef.Kind.DATA) {
            throw new BusinessException(ErrorCodes.ATTRIBUTE_INVALID, 400, "atributo é documento; use upload");
        }
        var inFlow = onboarding.execute(account).steps().stream()
                .flatMap(s -> s.attributes().stream())
                .anyMatch(a -> a.def().id.equals(def.id));
        if (!inFlow) {
            throw new BusinessException(ErrorCodes.ATTRIBUTE_NOT_IN_FLOW, 400, "atributo não faz parte do seu fluxo");
        }
        String v = value == null ? "" : value.trim();
        if (def.rules != null && !v.isEmpty()) {
            try {
                var rules = new com.fasterxml.jackson.databind.ObjectMapper().readTree(def.rules);
                var max = rules.path("maxLength");
                if (max.isInt() && v.length() > max.asInt()) {
                    throw new BusinessException(ErrorCodes.ATTRIBUTE_VALUE_INVALID, 400,
                            "valor excede " + max.asInt() + " caracteres", code);
                }
                var regex = rules.path("regex");
                if (regex.isTextual() && !Pattern.matches(regex.asText(), v)) {
                    throw new BusinessException(ErrorCodes.ATTRIBUTE_VALUE_INVALID, 400,
                            "formato inválido", code);
                }
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                // rules malformadas não bloqueiam — já validado no admin
            }
        }
        values.upsert(company.id, def.id, v);
        audit.log(account.id, account.email, "ATTRIBUTE_SET", "company", company.id.toString(), code);
    }
}
