package com.ecolchain.api.onboarding.application.usecase;

import com.ecolchain.api.catalog.application.port.out.CatalogStore;
import com.ecolchain.api.catalog.domain.AttributeDef;
import com.ecolchain.api.common.web.BusinessException;
import com.ecolchain.api.common.web.ErrorCodes;
import com.ecolchain.api.documents.application.port.out.DocumentStore;
import com.ecolchain.api.documents.domain.CompanyDocument;
import com.ecolchain.api.identity.domain.Account;
import com.ecolchain.api.onboarding.application.port.out.AttributeValueStore;
import com.ecolchain.api.onboarding.application.port.out.AuditStore;
import com.ecolchain.api.onboarding.application.port.out.CompanyStore;
import com.ecolchain.api.onboarding.domain.Company;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.UUID;

/** Revisão manual admin: APPROVE / REQUEST_CHANGES / REJECT / SUSPEND + decisão por item. docs/features/revisao-manual.md */
@ApplicationScoped
public class ReviewCompanyUseCase {

    public enum Decision { APPROVE, REQUEST_CHANGES, REJECT, SUSPEND }
    public enum ItemDecision { ACCEPT, REJECT }
    public record Item(String attributeCode, ItemDecision decision, String note) {}

    @Inject CompanyStore companies;
    @Inject AuditStore audit;
    @Inject CatalogStore catalog;
    @Inject DocumentStore documents;
    @Inject AttributeValueStore values;

    @Transactional
    public Company execute(Account admin, UUID companyId, Decision decision, String note,
                           List<Item> items) {
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
        applyItems(admin, company, items);
        return company;
    }

    /**
     * Revisão por item (contrato `itens[]`): ACCEPT/REJECT sobre um atributo do
     * fluxo da empresa — DOCUMENT muda o status do arquivo, DATA muda a revisão
     * do valor gravado. Item fora do fluxo ou sem conteúdo → REVIEW_ITEM_INVALID.
     */
    private void applyItems(Account admin, Company company, List<Item> items) {
        if (items == null || items.isEmpty()) return;
        var flowAttrIds = company.profileTypeCodes.stream()
                .map(catalog::profileTypeByCode)
                .flatMap(java.util.Optional::stream)
                .flatMap(pt -> catalog.flowOf(pt.id).stream())
                .flatMap(step -> step.attributes().stream())
                .map(fa -> fa.attribute().id)
                .collect(java.util.stream.Collectors.toSet());

        for (var item : items) {
            var def = catalog.attributeByCode(item.attributeCode())
                    .orElseThrow(() -> new BusinessException(ErrorCodes.REVIEW_ITEM_INVALID, 400,
                            "atributo inexistente: " + item.attributeCode()));
            if (!flowAttrIds.contains(def.id)) {
                throw new BusinessException(ErrorCodes.REVIEW_ITEM_INVALID, 400,
                        "atributo fora do fluxo da empresa: " + item.attributeCode());
            }
            String status = item.decision() == ItemDecision.ACCEPT ? "ACCEPTED" : "REJECTED";
            if (def.kind == AttributeDef.Kind.DOCUMENT) {
                var doc = documents.byCompanyAndAttribute(company.id, def.id)
                        .filter(d -> d.status == CompanyDocument.Status.UPLOADED
                                || d.status == CompanyDocument.Status.ACCEPTED
                                || d.status == CompanyDocument.Status.REJECTED)
                        .orElseThrow(() -> new BusinessException(ErrorCodes.REVIEW_ITEM_INVALID, 400,
                                "documento sem upload para revisar: " + item.attributeCode()));
                doc.status = CompanyDocument.Status.valueOf(status);
                doc.reviewNote = item.note();
                documents.save(doc);
            } else {
                values.find(company.id, def.id)
                        .filter(v -> v.valueText() != null && !v.valueText().isBlank())
                        .orElseThrow(() -> new BusinessException(ErrorCodes.REVIEW_ITEM_INVALID, 400,
                                "atributo sem valor para revisar: " + item.attributeCode()));
                values.setReview(company.id, def.id, status, item.note());
            }
            audit.log(admin.id, admin.email, "ITEM_REVIEW_" + status, "company",
                    company.id.toString(), item.attributeCode()
                            + (item.note() != null ? " — " + item.note() : ""));
        }
    }
}
