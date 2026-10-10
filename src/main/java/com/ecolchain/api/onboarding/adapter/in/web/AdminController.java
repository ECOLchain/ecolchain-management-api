package com.ecolchain.api.onboarding.adapter.in.web;

import com.ecolchain.api.catalog.application.port.out.CatalogStore;
import com.ecolchain.api.catalog.application.usecase.AdminCatalogUseCases;
import com.ecolchain.api.catalog.domain.AttributeDef;
import com.ecolchain.api.common.security.CurrentAccount;
import com.ecolchain.api.common.web.BusinessException;
import com.ecolchain.api.common.web.ErrorCodes;
import com.ecolchain.api.contract.api.AdminApi;
import com.ecolchain.api.contract.model.*;
import com.ecolchain.api.documents.application.usecase.DocumentUseCases;
import com.ecolchain.api.identity.application.port.out.AccountStore;
import com.ecolchain.api.identity.domain.Account;
import com.ecolchain.api.onboarding.application.port.out.AuditStore;
import com.ecolchain.api.onboarding.application.port.out.CompanyStore;
import com.ecolchain.api.onboarding.application.usecase.GetOnboardingUseCase;
import com.ecolchain.api.onboarding.application.usecase.ReviewCompanyUseCase;
import com.ecolchain.api.onboarding.domain.Company;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * /admin/** — papel re-checado no banco a cada request (proposta C.3),
 * mesmo com grupo PLATFORM_ADMIN no token.
 * docs/features/revisao-manual.md, admin-catalogo.md
 */
public class AdminController implements AdminApi {

    @Inject CurrentAccount current;
    @Inject CompanyStore companies;
    @Inject CatalogStore catalog;
    @Inject AdminCatalogUseCases adminCatalog;
    @Inject ReviewCompanyUseCase review;
    @Inject DocumentUseCases documents;
    @Inject AuditStore audit;
    @Inject AccountStore accounts;
    @Inject GetOnboardingUseCase onboarding;

    private Account admin() {
        var a = current.require();
        if (a.role != Account.Role.PLATFORM_ADMIN) {
            throw new BusinessException(ErrorCodes.FORBIDDEN, 403, "perfil sem acesso a este recurso");
        }
        return a;
    }

    // ---------- empresas ----------

    @Override
    public Response listCompanies(CompanyStatus status, Integer pagina, Integer tamanho) {
        admin();
        var st = status == null ? null : Company.Status.valueOf(status.name());
        var items = companies.listByStatus(st, pagina, tamanho).stream().map(c -> {
            var i = new AdminCompanyListItem();
            i.setId(c.id);
            i.setCnpj(c.cnpj);
            i.setRazaoSocial(c.razaoSocial);
            i.setStatus(CompanyStatus.valueOf(c.status.name()));
            i.setTiposPerfil(c.profileTypeCodes);
            if (c.createdAt != null) i.setCriadoEm(c.createdAt.atOffset(ZoneOffset.UTC));
            if (c.submittedAt != null) i.setSubmetidoEm(c.submittedAt.atOffset(ZoneOffset.UTC));
            return i;
        }).toList();
        var data = new AdminCompanyListResponseAllOfData();
        data.setEmpresas(items);
        data.setPagina(pagina);
        data.setTamanho(tamanho);
        data.setTotal(companies.countByStatus(st));
        var body = new AdminCompanyListResponse();
        body.setData(data);
        body.setLinks(List.of());
        body.setErros(List.of());
        return Response.ok(body).build();
    }

    @Override
    public Response getCompany(UUID id) {
        admin();
        var company = companies.byId(id)
                .orElseThrow(() -> new BusinessException(ErrorCodes.COMPANY_NOT_FOUND, 404, "empresa não encontrada"));
        var c = new AdminCompany();
        c.setId(company.id);
        c.setCnpj(company.cnpj);
        c.setRazaoSocial(company.razaoSocial);
        c.setCarteira(company.wallet);
        c.setStatus(CompanyStatus.valueOf(company.status.name()));
        c.setTiposPerfil(company.profileTypeCodes);
        if (company.createdAt != null) c.setCriadoEm(company.createdAt.atOffset(ZoneOffset.UTC));
        if (company.submittedAt != null) c.setSubmetidoEm(company.submittedAt.atOffset(ZoneOffset.UTC));
        var memberIds = companies.memberAccountIds(company.id);
        memberIds.stream().findFirst().flatMap(accounts::byId).ifPresent(owner -> {
            var contato = new AdminCompanyContato();
            contato.setEmail(owner.email);
            contato.setNomeCompleto(owner.fullName);
            contato.setVersaoTermos(company.termsVersion);
            if (company.termsAcceptedAt != null) {
                contato.setTermosAceitosEm(company.termsAcceptedAt.atOffset(ZoneOffset.UTC));
            }
            c.setContato(contato);
        });
        c.setValores(valuesOf(company));
        var body = new AdminCompanyResponse();
        body.setData(c);
        body.setLinks(List.of());
        body.setErros(List.of());
        return Response.ok(body).build();
    }

    private List<AdminCompanyValue> valuesOf(Company company) {
        // união dos fluxos dos perfis da empresa (mesma regra do onboarding)
        var account = new Account();
        account.id = companies.memberAccountIds(company.id).stream().findFirst().orElse(null);
        if (account.id == null) return List.of();
        var member = accounts.byId(account.id).orElse(null);
        if (member == null) return List.of();
        var r = onboarding.execute(member);
        var mapper = new ObjectMapper();
        return r.steps().stream().flatMap(s -> s.attributes().stream()).map(attr -> {
            var v = new AdminCompanyValue();
            v.setCodigo(attr.def().code);
            v.setTipo(AttributeKind.valueOf(attr.def().kind.name()));
            v.setRotuloPt(attr.def().labelPt);
            v.setRotuloEn(attr.def().labelEn);
            v.setObrigatorio(attr.required());
            var val = r.values().get(attr.def().id);
            if (val != null) {
                v.setValor(val.valueText());
                var rev = new Revisao();
                rev.setStatus(Revisao.StatusEnum.fromValue(
                        val.reviewStatus() == null ? "PENDING" : val.reviewStatus()));
                rev.setNota(val.reviewNote());
                v.setRevisao(rev);
            }
            var doc = r.documents().get(attr.def().id);
            if (doc != null && doc.status != com.ecolchain.api.documents.domain.CompanyDocument.Status.PENDING_UPLOAD) {
                var rev = new Revisao();
                rev.setStatus(Revisao.StatusEnum.fromValue(doc.status.name()));
                rev.setNota(doc.reviewNote);
                v.setRevisao(rev);
                var d = new AdminCompanyValueDocumento();
                d.setNomeArquivo(doc.fileName);
                d.setContentType(doc.contentType);
                d.setTamanhoBytes(doc.sizeBytes);
                d.setStatus(AdminCompanyValueDocumento.StatusEnum.fromValue(doc.status.name()));
                if (doc.uploadedAt != null) {
                    d.setConfirmadoEm(doc.uploadedAt.atOffset(ZoneOffset.UTC));
                }
                v.setDocumento(d);
            }
            return v;
        }).toList();
    }

    @Override
    public Response getCompanyDocumentDownloadUrl(UUID id, String code) {
        admin();
        var r = documents.adminDownloadUrl(id, code);
        var data = new DownloadUrlResponseAllOfData();
        data.setUrl(r.url());
        data.setObjectKey(r.objectKey());
        data.setExpiraEm(r.expiresIn());
        var body = new DownloadUrlResponse();
        body.setData(data);
        body.setLinks(List.of());
        body.setErros(List.of());
        return Response.ok(body).build();
    }

    @Override
    public Response reviewCompany(UUID id, ReviewRequest req) {
        var admin = admin();
        var itens = req.getItens() == null ? List.<ReviewCompanyUseCase.Item>of()
                : req.getItens().stream()
                        .map(i -> new ReviewCompanyUseCase.Item(i.getAtributo(),
                                ReviewCompanyUseCase.ItemDecision.valueOf(i.getDecisao().name()),
                                i.getNota()))
                        .toList();
        review.execute(admin, id,
                ReviewCompanyUseCase.Decision.valueOf(req.getDecisao().name()), req.getNota(),
                itens);
        // Resposta = detalhe completo (o SPA redesenha a empresa inteira).
        return getCompany(id);
    }

    // ---------- catálogo ----------

    @Override
    public Response listProfileTypes() {
        admin();
        var data = new AdminProfileTypesResponseAllOfData();
        data.setTipos(adminCatalog.listTypes().stream().map(this::toAdminProfileType).toList());
        var body = new AdminProfileTypesResponse();
        body.setData(data);
        body.setLinks(List.of());
        body.setErros(List.of());
        return Response.ok(body).build();
    }

    @Override
    public Response getProfileType(String code) {
        admin();
        var body = new AdminProfileTypeResponse();
        body.setData(toAdminProfileType(adminCatalog.getType(code)));
        body.setLinks(List.of());
        body.setErros(List.of());
        return Response.ok(body).build();
    }

    @Override
    public Response createProfileType(ProfileTypeUpsertRequest req) {
        var admin = admin();
        var pt = adminCatalog.upsertType(admin, req.getCodigo(), req.getNomePt(), req.getNomeEn(),
                req.getDescricaoPt(), req.getDescricaoEn(), req.getAtivo(), req.getOrdem(), toFlow(req.getPassos()));
        var body = new AdminProfileTypeResponse();
        body.setData(toAdminProfileType(pt));
        body.setLinks(List.of());
        body.setErros(List.of());
        return Response.status(201).entity(body).build();
    }

    @Override
    public Response patchProfileType(String code, ProfileTypePatchRequest req) {
        var admin = admin();
        var pt = adminCatalog.patchType(admin, code, req.getNomePt(), req.getNomeEn(),
                req.getDescricaoPt(), req.getDescricaoEn(), req.getAtivo(), req.getOrdem());
        var body = new AdminProfileTypeResponse();
        body.setData(toAdminProfileType(pt));
        body.setLinks(List.of());
        body.setErros(List.of());
        return Response.ok(body).build();
    }

    @Override
    public Response replaceProfileTypeFlow(String code, FlowReplaceRequest req) {
        var admin = admin();
        var pt = adminCatalog.replaceFlow(admin, adminCatalog.getType(code), toFlow(req.getPassos()));
        var body = new AdminProfileTypeResponse();
        body.setData(toAdminProfileType(pt));
        body.setLinks(List.of());
        body.setErros(List.of());
        return Response.ok(body).build();
    }

    private List<CatalogStore.FlowStep> toFlow(List<FlowStepInput> passos) {
        if (passos == null) return null;
        var out = new java.util.ArrayList<CatalogStore.FlowStep>();
        int spos = 0;
        for (var step : passos) {
            spos += 10;
            var attrs = new java.util.ArrayList<CatalogStore.FlowStep.FlowAttr>();
            int apos = 0;
            for (var fa : step.getAtributos()) {
                apos += 10;
                attrs.add(new CatalogStore.FlowStep.FlowAttr(resolveAttr(fa),
                        Boolean.TRUE.equals(fa.getObrigatorio()), apos));
            }
            out.add(new CatalogStore.FlowStep(step.getCodigo(), step.getTituloPt(),
                    step.getTituloEn(), spos, attrs));
        }
        return out;
    }

    private AttributeDef resolveAttr(FlowAttributeInput fa) {
        if (fa.getDefinicao() != null) {
            var input = fa.getDefinicao();
            var def = catalog.attributeByCode(fa.getCodigo()).orElseGet(AttributeDef::new);
            def.code = fa.getCodigo();
            def.kind = AttributeDef.Kind.valueOf(input.getTipo().name());
            def.labelPt = input.getRotuloPt();
            def.labelEn = input.getRotuloEn();
            def.helpPt = input.getAjudaPt();
            def.helpEn = input.getAjudaEn();
            def.rules = input.getRegras() == null ? null : writeJson(input.getRegras());
            def.active = true;
            return catalog.saveAttribute(def);
        }
        return catalog.attributeByCode(fa.getCodigo())
                .orElseThrow(() -> new BusinessException(ErrorCodes.ATTRIBUTE_INVALID, 400,
                        "atributo inexistente: " + fa.getCodigo()));
    }

    private String writeJson(Object o) {
        try { return new ObjectMapper().writeValueAsString(o); } catch (Exception e) { return null; }
    }

    private AdminProfileType toAdminProfileType(com.ecolchain.api.catalog.domain.ProfileType pt) {
        var out = new AdminProfileType();
        out.setCodigo(pt.code);
        out.setNomePt(pt.namePt);
        out.setNomeEn(pt.nameEn);
        out.setDescricaoPt(pt.descPt);
        out.setDescricaoEn(pt.descEn);
        out.setAtivo(pt.active);
        out.setOrdem(pt.position);
        var flow = catalog.flowOf(pt.id);
        out.setPassos(flow.stream().map(step -> {
            var s = new AdminFlowStep();
            s.setCodigo(step.code());
            s.setTituloPt(step.titlePt());
            s.setTituloEn(step.titleEn());
            s.setAtributos(step.attributes().stream().map(fa -> {
                var a = new AdminFlowAttribute();
                a.setCodigo(fa.attribute().code);
                a.setObrigatorio(fa.required());
                a.setDefinicao(toAdminAttribute(fa.attribute()));
                return a;
            }).toList());
            return s;
        }).toList());
        return out;
    }

    // ---------- atributos ----------

    @Override
    public Response listAttributes() {
        admin();
        var data = new AdminAttributesResponseAllOfData();
        data.setAtributos(catalog.listAttributes(false).stream().map(this::toAdminAttribute).toList());
        var body = new AdminAttributesResponse();
        body.setData(data);
        body.setLinks(List.of());
        body.setErros(List.of());
        return Response.ok(body).build();
    }

    @Override
    public Response createAttribute(AttributeDefinitionRequest req) {
        var admin = admin();
        var def = adminCatalog.upsertAttribute(admin, req.getCodigo(), req.getTipo().name(),
                req.getRotuloPt(), req.getRotuloEn(), req.getAjudaPt(), req.getAjudaEn(),
                req.getRegras() == null ? null : writeJson(req.getRegras()), req.getAtivo());
        var body = new AdminAttributeResponse();
        body.setData(toAdminAttribute(def));
        body.setLinks(List.of());
        body.setErros(List.of());
        return Response.status(201).entity(body).build();
    }

    @Override
    public Response upsertAttribute(String code, AttributeDefinitionRequest req) {
        return createAttribute(req.codigo(code));
    }

    private AdminAttribute toAdminAttribute(AttributeDef def) {
        var a = new AdminAttribute();
        a.setCodigo(def.code);
        a.setTipo(AttributeKind.valueOf(def.kind.name()));
        a.setRotuloPt(def.labelPt);
        a.setRotuloEn(def.labelEn);
        a.setAjudaPt(def.helpPt);
        a.setAjudaEn(def.helpEn);
        if (def.rules != null) {
            try { a.setRegras(new ObjectMapper().readValue(def.rules, AttributeRules.class)); }
            catch (Exception ignored) {}
        }
        a.setAtivo(def.active);
        return a;
    }

    // ---------- auditoria ----------

    @Override
    public Response listAuditLog(Integer pagina, Integer tamanho) {
        admin();
        var data = new AuditLogResponseAllOfData();
        data.setItens(audit.list(pagina, tamanho).stream().map(e -> {
            var i = new AuditLogItem();
            i.setId(e.id());
            i.setAtor(e.actorEmail());
            i.setAcao(e.action());
            i.setEntidade(e.entityType());
            i.setEntidadeId(e.entityId());
            i.setDepois(e.detail());
            i.setCriadoEm(e.createdAt().atOffset(ZoneOffset.UTC));
            return i;
        }).toList());
        var body = new AuditLogResponse();
        body.setData(data);
        body.setLinks(List.of());
        body.setErros(List.of());
        return Response.ok(body).build();
    }

    // ---------- contas admin ----------

    private AdminAccount adminAccountOf(Account a) {
        var m = new AdminAccount();
        m.setId(a.id);
        m.setEmail(a.email);
        m.setNomeCompleto(a.fullName);
        m.setTelefone(a.phone);
        m.setPapel(AccountRole.valueOf(a.role.name()));
        m.setCriadoEm(a.createdAt.atOffset(ZoneOffset.UTC));
        return m;
    }

    @Override
    public Response listAdminAccounts() {
        admin();
        var data = new AdminAccountsResponseAllOfData();
        data.setAdmins(accounts.listByRole(Account.Role.PLATFORM_ADMIN).stream()
                .map(this::adminAccountOf).toList());
        var body = new AdminAccountsResponse();
        body.setData(data);
        body.setLinks(List.of());
        body.setErros(List.of());
        return Response.ok(body).build();
    }

    @Override
    @jakarta.transaction.Transactional
    public Response createAdminAccount(AdminAccountCreateRequest req) {
        var actor = admin();
        String email = req.getEmail() == null ? "" : req.getEmail().trim().toLowerCase();
        if (email.isEmpty() || req.getNomeCompleto() == null || req.getNomeCompleto().isBlank()
                || req.getTelefone() == null || req.getTelefone().isBlank()) {
            throw new BusinessException(ErrorCodes.VALIDATION, 400,
                    "email, nomeCompleto e telefone são obrigatórios");
        }
        if (accounts.byEmail(email).isPresent()) {
            throw new BusinessException(ErrorCodes.ACCOUNT_IN_USE, 409,
                    "já existe uma conta com este e-mail");
        }
        var a = Account.verified(email, Account.Role.PLATFORM_ADMIN);
        a.fullName = req.getNomeCompleto().trim();
        a.phone = req.getTelefone().trim();
        a = accounts.save(a);
        audit.log(actor.id, actor.email, "ADMIN_ACCOUNT_CREATED", "account",
                a.id.toString(), email);
        var body = new AdminAccountResponse();
        body.setData(adminAccountOf(a));
        body.setLinks(List.of());
        body.setErros(List.of());
        return Response.status(Response.Status.CREATED).entity(body).build();
    }
}
