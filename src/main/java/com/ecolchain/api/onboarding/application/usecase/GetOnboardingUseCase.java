package com.ecolchain.api.onboarding.application.usecase;

import com.ecolchain.api.catalog.application.port.out.CatalogStore;
import com.ecolchain.api.catalog.domain.AttributeDef;
import com.ecolchain.api.common.web.BusinessException;
import com.ecolchain.api.common.web.ErrorCodes;
import com.ecolchain.api.documents.application.port.out.DocumentStore;
import com.ecolchain.api.documents.domain.CompanyDocument;
import com.ecolchain.api.identity.domain.Account;
import com.ecolchain.api.onboarding.application.port.out.AttributeValueStore;
import com.ecolchain.api.onboarding.application.port.out.CompanyStore;
import com.ecolchain.api.onboarding.domain.Company;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Monta o onboarding = união dos fluxos dos tipos de perfil da empresa
 * (dedup por código de passo; atributo requerido vence opcional).
 * docs/features/onboarding-dinamico.md
 */
@ApplicationScoped
public class GetOnboardingUseCase {

    public record Step(String code, String titlePt, String titleEn, List<Attr> attributes) {
        public record Attr(AttributeDef def, boolean required) {}
    }

    public record Result(Company company, List<String> profileNamesPt, List<Step> steps,
                         int requiredTotal, int requiredFilled,
                         Map<UUID, AttributeValueStore.Value> values,
                         Map<UUID, CompanyDocument> documents) {}

    @Inject CompanyStore companies;
    @Inject CatalogStore catalog;
    @Inject AttributeValueStore values;
    @Inject DocumentStore documents;

    public Result execute(Account account) {
        var company = companies.byMemberAccountId(account.id)
                .orElseThrow(() -> new BusinessException(ErrorCodes.COMPANY_NOT_FOUND, 404,
                        "empresa não cadastrada"));

        var profiles = company.profileTypeCodes.stream()
                .map(code -> catalog.profileTypeByCode(code))
                .flatMap(Optional::stream)
                .toList();

        // união de passos por código (merge atributos)
        Map<String, StepAcc> merged = new LinkedHashMap<>();
        List<String> names = new ArrayList<>();
        for (var pt : profiles) {
            names.add(pt.namePt);
            for (var flow : catalog.flowOf(pt.id)) {
                var acc = merged.computeIfAbsent(flow.code(),
                        k -> new StepAcc(flow.code(), flow.titlePt(), flow.titleEn()));
                for (var fa : flow.attributes()) {
                    acc.merge(fa.attribute().code, new Step.Attr(fa.attribute(), fa.required()));
                }
            }
        }
        List<Step> steps = merged.values().stream().map(StepAcc::toStep).toList();

        var valueMap = values.all(company.id);
        var docMap = documents.byCompany(company.id).stream()
                .collect(Collectors.toMap(d -> d.attributeId, d -> d));

        int total = 0, filled = 0;
        for (var step : steps) {
            for (var attr : step.attributes()) {
                if (!attr.def().active || !attr.required()) continue;
                total++;
                boolean ok = attr.def().kind == AttributeDef.Kind.DATA
                        ? valueMap.containsKey(attr.def().id) && valueMap.get(attr.def().id).valueText() != null
                            && !valueMap.get(attr.def().id).valueText().isBlank()
                        : docMap.containsKey(attr.def().id)
                            && docMap.get(attr.def().id).status == CompanyDocument.Status.UPLOADED;
                if (ok) filled++;
            }
        }
        return new Result(company, names, steps, total, filled, valueMap, docMap);
    }

    private static class StepAcc {
        final String code; final String pt; final String en;
        final Map<String, Step.Attr> attrs = new LinkedHashMap<>();
        StepAcc(String code, String pt, String en) { this.code = code; this.pt = pt; this.en = en; }
        void merge(String key, Step.Attr attr) {
            attrs.merge(key, attr, (a, b) -> a.required() || b.required()
                    ? new Step.Attr(a.def(), true) : a);
        }
        Step toStep() { return new Step(code, pt, en, List.copyOf(attrs.values())); }
    }
}
