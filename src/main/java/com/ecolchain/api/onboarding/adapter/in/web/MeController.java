package com.ecolchain.api.onboarding.adapter.in.web;

import com.ecolchain.api.catalog.application.port.out.CatalogStore;
import com.ecolchain.api.catalog.domain.AttributeDef;
import com.ecolchain.api.common.i18n.RequestLocale;
import com.ecolchain.api.common.security.CurrentAccount;
import com.ecolchain.api.contract.api.MeApi;
import com.ecolchain.api.contract.model.*;
import com.ecolchain.api.documents.application.usecase.DocumentUseCases;
import com.ecolchain.api.documents.domain.CompanyDocument;
import com.ecolchain.api.identity.domain.Account;
import com.ecolchain.api.onboarding.application.usecase.*;
import com.ecolchain.api.onboarding.domain.Company;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

/** Endpoints do usuário logado (/me/**). docs/features/cadastro-empresa.md, onboarding-dinamico.md, documentos-upload.md */
public class MeController implements MeApi {

    @Inject CurrentAccount current;
    @Inject UpsertCompanyUseCase upsertCompany;
    @Inject GetOnboardingUseCase onboarding;
    @Inject PutAttributeValueUseCase putAttribute;
    @Inject SubmitOnboardingUseCase submit;
    @Inject DocumentUseCases documents;
    @Inject RequestLocale locale;
    @Inject CatalogStore catalog;

    private ContaInfo contaOf(Account a) {
        var c = new ContaInfo();
        c.setEmail(a.email);
        c.setPapel(AccountRole.valueOf(a.role.name()));
        c.setNomeCompleto(a.fullName);
        return c;
    }

    private EmpresaResumo empresaOf(Company c) {
        var e = new EmpresaResumo();
        e.setId(c.id);
        e.setCnpj(c.cnpj);
        e.setRazaoSocial(c.razaoSocial);
        e.setStatus(CompanyStatus.valueOf(c.status.name()));
        e.setTiposPerfil(c.profileTypeCodes);
        return e;
    }

    @Override
    public Response getMe() {
        var account = current.require();
        var company = com.ecolchain.api.onboarding.application.usecase.NextStep.COMPANY_FORM;
        var c = companyOf(account);
        var data = new MeResponseAllOfData();
        data.setConta(contaOf(account));
        data.setEmpresa(c == null ? null : empresaOf(c));
        data.setProximoPasso(com.ecolchain.api.contract.model.NextStep.valueOf(
                com.ecolchain.api.onboarding.application.usecase.NextStep.of(c).name()));
        var body = new MeResponse();
        body.setData(data);
        body.setLinks(List.of());
        body.setErros(List.of());
        return Response.ok(body).build();
    }

    private Company companyOf(Account a) {
        try {
            return onboarding.execute(a).company();
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public Response upsertCompany(CompanyUpsertRequest req) {
        var account = current.require();
        var company = upsertCompany.execute(account, req.getCnpj(), req.getRazaoSocial(),
                req.getNomeCompleto(), req.getTiposPerfil(), req.getDeclaracaoRepresentante(),
                req.getVersaoTermos());
        var body = new CompanyResponse();
        body.setData(empresaOf(company));
        body.setLinks(List.of());
        body.setErros(List.of());
        return Response.ok(body).build();
    }

    @Override
    public Response getOnboarding() {
        var account = current.require();
        var r = onboarding.execute(account);
        boolean en = locale.isEn();
        var data = new OnboardingResponseAllOfData();
        data.setStatus(CompanyStatus.valueOf(r.company().status.name()));
        data.setTiposPerfil(r.company().profileTypeCodes.stream().map(code -> {
            var s = new ProfileTypeSummary();
            s.setCodigo(code);
            catalog.profileTypeByCode(code).ifPresent(pt -> {
                s.setNome(en ? pt.nameEn : pt.namePt);
                s.setDescricao(en ? pt.descEn : pt.descPt);
                s.setOrdem(pt.position);
            });
            return s;
        }).toList());
        data.setPassos(r.steps().stream().map(step -> {
            var s = new OnboardingStep();
            s.setCodigo(step.code());
            s.setTitulo(en ? step.titleEn() : step.titlePt());
            s.setAtributos(step.attributes().stream().map(attr -> {
                var a = new OnboardingAttribute();
                a.setCodigo(attr.def().code);
                a.setTipo(AttributeKind.valueOf(attr.def().kind.name()));
                a.setRotulo(en ? attr.def().labelEn : attr.def().labelPt);
                a.setAjuda(en ? attr.def().helpEn : attr.def().helpPt);
                a.setObrigatorio(attr.required());
                a.setRegras(rulesOf(attr.def()));
                var v = r.values().get(attr.def().id);
                if (v != null) a.setValor(v.valueText());
                var doc = r.documents().get(attr.def().id);
                if (doc != null && doc.status != CompanyDocument.Status.PENDING_UPLOAD) {
                    var rev = new Revisao();
                    rev.setStatus(Revisao.StatusEnum.fromValue(doc.status.name()));
                    rev.setNota(doc.reviewNote);
                    a.setRevisao(rev);
                }
                return a;
            }).toList());
            return s;
        }).toList());
        var prog = new Progresso();
        prog.setTotalObrigatorios(r.requiredTotal());
        prog.setPreenchidosObrigatorios(r.requiredFilled());
        data.setProgresso(prog);
        var body = new OnboardingResponse();
        body.setData(data);
        body.setLinks(List.of());
        body.setErros(List.of());
        return Response.ok(body).build();
    }

    private AttributeRules rulesOf(AttributeDef def) {
        if (def.rules == null) return null;
        try {
            return new ObjectMapper().readValue(def.rules, AttributeRules.class);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public Response putMyAttribute(String code, AttributeValueRequest req) {
        putAttribute.execute(current.require(), code, req.getValor());
        var body = new EmptyResponse();
        body.setLinks(List.of());
        body.setErros(List.of());
        return Response.ok(body).build();
    }

    @Override
    public Response submitOnboarding() {
        var company = submit.execute(current.require());
        var data = new SubmitResponseAllOfData();
        data.setStatus(CompanyStatus.valueOf(company.status.name()));
        var body = new SubmitResponse();
        body.setData(data);
        body.setLinks(List.of());
        body.setErros(List.of());
        return Response.ok(body).build();
    }

    @Override
    public Response createUploadUrl(String code, UploadUrlRequest req) {
        var r = documents.createUploadUrl(current.require(), code,
                req.getNomeArquivo(), req.getContentType(), req.getTamanhoBytes());
        var data = new UploadUrlResponseAllOfData();
        data.setUrl(r.url());
        data.setObjectKey(r.objectKey());
        data.setExpiraEm(r.expiresIn());
        data.setContentType(r.contentType());
        data.setTamanhoBytes(r.sizeBytes());
        var body = new UploadUrlResponse();
        body.setData(data);
        body.setLinks(List.of());
        body.setErros(List.of());
        return Response.ok(body).build();
    }

    @Override
    public Response confirmUpload(String code, ConfirmUploadRequest req) {
        documents.confirmUpload(current.require(), code, req.getObjectKey());
        var body = new EmptyResponse();
        body.setLinks(List.of());
        body.setErros(List.of());
        return Response.ok(body).build();
    }

    @Override
    public Response getMyDownloadUrl(String code) {
        var r = documents.myDownloadUrl(current.require(), code);
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
}
