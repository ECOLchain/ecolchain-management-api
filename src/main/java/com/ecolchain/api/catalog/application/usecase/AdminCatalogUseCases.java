package com.ecolchain.api.catalog.application.usecase;

import com.ecolchain.api.catalog.application.port.out.CatalogStore;
import com.ecolchain.api.catalog.domain.AttributeDef;
import com.ecolchain.api.catalog.domain.ProfileType;
import com.ecolchain.api.common.web.BusinessException;
import com.ecolchain.api.common.web.ErrorCodes;
import com.ecolchain.api.identity.domain.Account;
import com.ecolchain.api.onboarding.application.port.out.AuditStore;
import com.ecolchain.api.onboarding.application.port.out.CompanyStore;
import com.ecolchain.api.onboarding.domain.Company;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.UUID;

/** CRUD admin do catálogo + replace de fluxo (dispara PENDING_UPDATE em APPROVED). docs/features/admin-catalogo.md */
@ApplicationScoped
public class AdminCatalogUseCases {

    @Inject CatalogStore catalog;
    @Inject CompanyStore companies;
    @Inject AuditStore audit;

    public List<ProfileType> listTypes() { return catalog.listProfileTypes(false); }

    public ProfileType getType(String code) {
        return catalog.profileTypeByCode(code.toUpperCase())
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, 404, "tipo de perfil não encontrado"));
    }

    @Transactional
    public ProfileType upsertType(Account actor, String code, String namePt, String nameEn,
                                  String descPt, String descEn, Boolean active, Integer position,
                                  List<CatalogStore.FlowStep> steps) {
        var pt = catalog.profileTypeByCode(code.toUpperCase()).orElseGet(ProfileType::new);
        boolean isNew = pt.id == null;
        pt.code = code.toUpperCase();
        pt.namePt = namePt; pt.nameEn = nameEn;
        pt.descPt = descPt; pt.descEn = descEn;
        pt.active = active == null || active;
        pt.position = position == null ? 0 : position;
        catalog.saveProfileType(pt);
        if (steps != null) {
            replaceFlow(actor, pt, steps);
        }
        audit.log(actor.id, actor.email, isNew ? "PROFILE_TYPE_CREATED" : "PROFILE_TYPE_UPDATED",
                "profile_type", pt.id.toString(), pt.code);
        return pt;
    }

    @Transactional
    public ProfileType patchType(Account actor, String code, String namePt, String nameEn,
                                 String descPt, String descEn, Boolean active, Integer position) {
        var pt = getType(code);
        if (active != null && !active && catalog.profileTypeInUse(pt.id)) {
            throw new BusinessException(ErrorCodes.PROFILE_TYPE_IN_USE, 409,
                    "tipo de perfil em uso por empresas; não pode ser desativado");
        }
        if (namePt != null) pt.namePt = namePt;
        if (nameEn != null) pt.nameEn = nameEn;
        if (descPt != null) pt.descPt = descPt;
        if (descEn != null) pt.descEn = descEn;
        if (active != null) pt.active = active;
        if (position != null) pt.position = position;
        catalog.saveProfileType(pt);
        audit.log(actor.id, actor.email, "PROFILE_TYPE_PATCHED", "profile_type", pt.id.toString(), pt.code);
        return pt;
    }

    @Transactional
    public ProfileType replaceFlow(Account actor, ProfileType pt, List<CatalogStore.FlowStep> steps) {
        // valida atributos existem
        for (var step : steps) {
            for (var attr : step.attributes()) {
                if (attr.attribute() == null || attr.attribute().id == null) {
                    throw new BusinessException(ErrorCodes.ATTRIBUTE_INVALID, 400,
                            "atributo inexistente no fluxo");
                }
            }
        }
        catalog.replaceFlow(pt.id, steps);
        // novo atributo obrigatório → empresas APPROVED com este perfil passam a PENDING_UPDATE
        markApprovedForUpdate(pt.id);
        audit.log(actor.id, actor.email, "PROFILE_TYPE_FLOW_REPLACED",
                "profile_type", pt.id.toString(), pt.code);
        return pt;
    }

    private void markApprovedForUpdate(UUID profileTypeId) {
        // marca APPROVED vinculadas como PENDING_UPDATE
        var approved = companies.listByStatus(Company.Status.APPROVED, 0, 500);
        for (var c : approved) {
            var ptIds = com.ecolchain.api.onboarding.adapter.out.persistence.CompanyProfileEntity
                    .<com.ecolchain.api.onboarding.adapter.out.persistence.CompanyProfileEntity>
                    find("companyId", c.id).list().stream().map(p -> p.profileTypeId).toList();
            if (ptIds.contains(profileTypeId)) {
                c.status = Company.Status.PENDING_UPDATE;
                companies.save(c);
            }
        }
    }

    public List<AttributeDef> listAttributes() { return catalog.listAttributes(false); }

    @Transactional
    public AttributeDef upsertAttribute(Account actor, String code, String kind, String labelPt, String labelEn,
                                        String helpPt, String helpEn, String rules, Boolean active) {
        var def = catalog.attributeByCode(code).orElseGet(AttributeDef::new);
        def.code = code;
        def.kind = AttributeDef.Kind.valueOf(kind);
        def.labelPt = labelPt; def.labelEn = labelEn;
        def.helpPt = helpPt; def.helpEn = helpEn;
        def.rules = rules;
        def.active = active == null || active;
        catalog.saveAttribute(def);
        audit.log(actor.id, actor.email, "ATTRIBUTE_UPSERTED", "attribute", def.id.toString(), code);
        return def;
    }
}
