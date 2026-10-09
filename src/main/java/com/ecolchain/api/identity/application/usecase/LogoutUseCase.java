package com.ecolchain.api.identity.application.usecase;

import com.ecolchain.api.identity.application.port.out.RefreshTokenStore;
import com.ecolchain.api.identity.domain.RefreshToken;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/** Revoga o refresh do cookie (idempotente). docs/features/auth-sessao.md */
@ApplicationScoped
public class LogoutUseCase {

    @Inject RefreshTokenStore refreshes;

    @Transactional
    public void execute(String cookieValue) {
        if (cookieValue == null || !cookieValue.contains(".")) {
            return;
        }
        String secret = cookieValue.substring(cookieValue.indexOf('.') + 1);
        refreshes.byHash(RefreshToken.sha256(secret))
                .ifPresent(t -> refreshes.revokeFamily(t.familyId, null));
    }
}
