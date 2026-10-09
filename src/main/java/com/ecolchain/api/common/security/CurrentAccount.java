package com.ecolchain.api.common.security;

import com.ecolchain.api.common.web.BusinessException;
import com.ecolchain.api.common.web.ErrorCodes;
import com.ecolchain.api.identity.application.port.out.AccountStore;
import com.ecolchain.api.identity.domain.Account;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.quarkus.security.identity.SecurityIdentity;
import java.util.UUID;

/** Resolve o Account autenticado (sub = UUID da conta) ou falha 401/403. */
@ApplicationScoped
public class CurrentAccount {

    @Inject SecurityIdentity security;
    @Inject AccountStore accounts;

    public Account require() {
        var caller = security.getPrincipal();
        if (caller == null) {
            throw new BusinessException(ErrorCodes.AUTH_REQUIRED, 401, "autenticação necessária");
        }
        UUID id;
        try {
            id = UUID.fromString(caller.getName());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCodes.AUTH_REQUIRED, 401, "token inválido");
        }
        var account = accounts.byId(id)
                .orElseThrow(() -> new BusinessException(ErrorCodes.AUTH_REQUIRED, 401, "conta não encontrada"));
        if (account.blocked()) {
            throw new BusinessException(ErrorCodes.ACCOUNT_BLOCKED, 403, "conta bloqueada");
        }
        return account;
    }
}
