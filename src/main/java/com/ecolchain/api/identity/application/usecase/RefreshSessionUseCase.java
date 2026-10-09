package com.ecolchain.api.identity.application.usecase;

import com.ecolchain.api.common.web.BusinessException;
import com.ecolchain.api.common.web.ErrorCodes;
import com.ecolchain.api.identity.application.AuthPolicy;
import com.ecolchain.api.identity.application.port.out.*;
import com.ecolchain.api.identity.domain.RefreshToken;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.UUID;

/**
 * Rotação do refresh: token apresentado é substituído por um novo.
 * Reuso (token já rotacionado) revoga a família inteira — possível roubo.
 * docs/features/auth-sessao.md
 */
@ApplicationScoped
public class RefreshSessionUseCase {

    public record Result(String accessToken, long accessTtlSeconds,
                         String refreshSecret, long refreshTtlSeconds) {}

    @Inject AuthPolicy policy;
    @Inject RefreshTokenStore refreshes;
    @Inject AccountStore accounts;
    @Inject AccessTokenIssuer issuer;

    @Transactional
    public Result execute(String cookieValue) {
        if (cookieValue == null || !cookieValue.contains(".")) {
            throw new BusinessException(ErrorCodes.REFRESH_INVALID, 401, "sessão expirada; entre novamente");
        }
        String secret = cookieValue.substring(cookieValue.indexOf('.') + 1);
        var current = refreshes.byHash(RefreshToken.sha256(secret))
                .orElseThrow(() -> new BusinessException(ErrorCodes.REFRESH_INVALID, 401,
                        "sessão expirada; entre novamente"));

        Instant now = Instant.now();
        if (current.revokedAt != null) {
            // token antigo reapresentado após rotação → suspeita de roubo
            refreshes.revokeFamily(current.familyId, null);
            throw new BusinessException(ErrorCodes.REFRESH_REUSED, 401,
                    "sessão invalidada por segurança; entre novamente");
        }
        if (!current.active(now)) {
            throw new BusinessException(ErrorCodes.REFRESH_INVALID, 401, "sessão expirada; entre novamente");
        }
        var account = accounts.byId(current.accountId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.REFRESH_INVALID, 401, "conta não encontrada"));
        if (account.blocked()) {
            throw new BusinessException(ErrorCodes.ACCOUNT_BLOCKED, 403, "conta bloqueada");
        }

        var next = new RefreshToken();
        next.accountId = account.id;
        next.familyId = current.familyId;
        String newSecret = RefreshToken.newSecret();
        next.tokenHash = RefreshToken.sha256(newSecret);
        next.expiresAt = now.plus(policy.refresh().ttl());
        next.familyExpiresAt = current.familyExpiresAt;
        refreshes.save(next);
        refreshes.revoke(current.id, next.id); // revoga só o apresentado; novo fica ativo

        var issued = issuer.issue(account);
        return new Result(issued.token(), issued.expiresInSeconds(),
                next.familyId + "." + newSecret, policy.refresh().ttl().toSeconds());
    }
}
