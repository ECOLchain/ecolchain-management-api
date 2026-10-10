package com.ecolchain.api.onboarding.application.usecase;

import com.ecolchain.api.catalog.application.port.out.CatalogStore;
import com.ecolchain.api.common.web.BusinessException;
import com.ecolchain.api.common.web.ErrorCodes;
import com.ecolchain.api.identity.application.AuthPolicy;
import com.ecolchain.api.identity.application.port.out.AccountStore;
import com.ecolchain.api.identity.domain.Account;
import com.ecolchain.api.onboarding.application.port.out.AuditStore;
import com.ecolchain.api.onboarding.application.port.out.CompanyStore;
import com.ecolchain.api.onboarding.domain.CnpjValidator;
import com.ecolchain.api.onboarding.domain.Company;
import com.ecolchain.api.onboarding.domain.WalletValidator;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/**
 * Cria/atualiza a empresa da conta: CNPJ alfanumérico único (entre não-rejeitadas),
 * 1..N tipos de perfil válidos, termos + declaração obrigatórios.
 * docs/features/cadastro-empresa.md
 */
@ApplicationScoped
public class UpsertCompanyUseCase {

    @Inject CompanyStore companies;
    @Inject CatalogStore catalog;
    @Inject AccountStore accounts;
    @Inject AuditStore audit;
    @Inject AuthPolicy policy;

    @Transactional
    public Company execute(Account account, String cnpjRaw, String razaoSocial, String nomeCompleto,
                           String carteiraRaw, List<String> tiposPerfil, Boolean declaracao, String versaoTermos) {
        String cnpj = CnpjValidator.normalize(cnpjRaw);
        if (!CnpjValidator.isValid(cnpj)) {
            throw new BusinessException(ErrorCodes.CNPJ_INVALID, 400, "CNPJ inválido", "cnpj");
        }
        if (razaoSocial == null || razaoSocial.isBlank()) {
            throw new BusinessException(ErrorCodes.VALIDATION, 400, "razão social obrigatória", "razaoSocial");
        }
        if (nomeCompleto == null || nomeCompleto.isBlank()) {
            throw new BusinessException(ErrorCodes.VALIDATION, 400, "nome completo obrigatório", "nomeCompleto");
        }
        String carteira = WalletValidator.normalize(carteiraRaw);
        if (!WalletValidator.isValid(carteira)) {
            throw new BusinessException(ErrorCodes.WALLET_INVALID, 400,
                    "endereço de carteira inválido (Solana base58 ou 0x)", "carteira");
        }
        if (tiposPerfil == null || tiposPerfil.isEmpty()) {
            throw new BusinessException(ErrorCodes.VALIDATION, 400, "selecione ao menos um tipo de perfil", "tiposPerfil");
        }
        if (!Boolean.TRUE.equals(declaracao)) {
            throw new BusinessException(ErrorCodes.DECLARATION_REQUIRED, 400,
                    "declaração de representante legal obrigatória", "declaracaoRepresentante");
        }
        String terms = (versaoTermos == null || versaoTermos.isBlank()) ? policy.termsVersion() : versaoTermos;

        var codes = new LinkedHashSet<>(tiposPerfil.stream().map(String::toUpperCase).toList());
        var profileIds = codes.stream()
                .map(code -> catalog.profileTypeByCode(code)
                        .filter(p -> p.active)
                        .orElseThrow(() -> new BusinessException(ErrorCodes.PROFILE_TYPE_INVALID, 400,
                                "tipo de perfil inválido: " + code, "tiposPerfil")))
                .map(p -> p.id)
                .toList();

        var existing = companies.byMemberAccountId(account.id);
        Company company;
        if (existing.isPresent()) {
            company = existing.get();
            if (!company.editable()) {
                throw new BusinessException(ErrorCodes.COMPANY_LOCKED, 409,
                        "cadastro em análise não pode ser alterado");
            }
            if (!company.cnpj.equals(cnpj)) {
                companies.byCnpj(cnpj).filter(o -> !o.id.equals(company.id)).ifPresent(o -> {
                    throw new BusinessException(ErrorCodes.CNPJ_IN_USE, 409, "CNPJ já cadastrado", "cnpj");
                });
                company.cnpj = cnpj;
            }
            company.razaoSocial = razaoSocial.trim();
            company.wallet = carteira;
        } else {
            companies.byCnpj(cnpj).ifPresent(o -> {
                throw new BusinessException(ErrorCodes.CNPJ_IN_USE, 409, "CNPJ já cadastrado", "cnpj");
            });
            company = new Company();
            company.status = Company.Status.PENDING_DOCUMENTS;
            company.cnpj = cnpj;
            company.razaoSocial = razaoSocial.trim();
            company.wallet = carteira;
        }
        company.termsVersion = terms;
        company.termsAcceptedAt = Instant.now();
        boolean isNew = company.id == null;
        companies.save(company);
        if (isNew) {
            companies.addOwnerMember(company.id, account.id);
        }
        companies.setProfiles(company.id, profileIds);

        if (account.fullName == null || !account.fullName.equals(nomeCompleto.trim())) {
            account.fullName = nomeCompleto.trim();
            accounts.save(account);
        }
        audit.log(account.id, account.email, isNew ? "COMPANY_CREATED" : "COMPANY_UPDATED",
                "company", company.id.toString(), "cnpj=" + cnpj + " perfis=" + codes);
        company.profileTypeCodes = List.copyOf(codes);
        return company;
    }
}
