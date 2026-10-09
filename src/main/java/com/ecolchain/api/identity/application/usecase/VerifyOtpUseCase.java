package com.ecolchain.api.identity.application.usecase;

import com.ecolchain.api.common.web.BusinessException;
import com.ecolchain.api.common.web.ErrorCodes;
import com.ecolchain.api.identity.application.AuthPolicy;
import com.ecolchain.api.identity.application.port.out.*;
import com.ecolchain.api.identity.domain.Account;
import com.ecolchain.api.identity.domain.OtpCode;
import com.ecolchain.api.identity.domain.RefreshToken;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.UUID;

/**
 * Verifica o OTP (máx 5 tentativas, uso único) e emite sessão:
 * access JWT 15min + refresh rotativo (cookie). Conta nova nasce COMPANY_OWNER.
 * docs/features/auth-otp.md
 */
@ApplicationScoped
public class VerifyOtpUseCase {

    public record Result(Account account, String accessToken, long accessTtlSeconds,
                         String refreshSecret, long refreshTtlSeconds) {}

    @Inject AuthPolicy policy;
    @Inject OtpStore otps;
    @Inject AccountStore accounts;
    @Inject RefreshTokenStore refreshes;
    @Inject AccessTokenIssuer issuer;

    @Transactional
    public Result execute(String rawEmail, String code) {
        String email = rawEmail == null ? "" : rawEmail.trim().toLowerCase();
        var pending = otps.latestPendingFor(email)
                .orElseThrow(() -> new BusinessException(ErrorCodes.OTP_INVALID, 401,
                        "código inválido ou expirado"));
        Instant now = Instant.now();
        if (pending.expiresAt().isBefore(now)) {
            throw new BusinessException(ErrorCodes.OTP_INVALID, 401, "código inválido ou expirado");
        }
        if (pending.attempts() >= policy.otp().maxAttempts()) {
            throw new BusinessException(ErrorCodes.OTP_INVALID, 401, "tentativas esgotadas; peça um novo código");
        }
        if (code == null || !OtpCode.matches(code.trim(), pending.codeHmac(), policy.otp().pepper())) {
            otps.incrementAttempts(pending.id());
            throw new BusinessException(ErrorCodes.OTP_INVALID, 401, "código inválido ou expirado");
        }
        otps.consume(pending.id());

        Account account = accounts.byEmail(email)
                .orElseGet(() -> accounts.save(Account.verified(email, Account.Role.COMPANY_OWNER)));
        if (account.blocked()) {
            throw new BusinessException(ErrorCodes.ACCOUNT_BLOCKED, 403, "conta bloqueada");
        }

        var issued = issuer.issue(account);
        var refresh = new RefreshToken();
        refresh.accountId = account.id;
        refresh.familyId = UUID.randomUUID();
        String secret = RefreshToken.newSecret();
        refresh.tokenHash = RefreshToken.sha256(secret);
        refresh.expiresAt = now.plus(policy.refresh().ttl());
        refresh.familyExpiresAt = now.plus(policy.refresh().familyTtl());
        refreshes.save(refresh);

        return new Result(account, issued.token(), issued.expiresInSeconds(),
                refresh.familyId + "." + secret, policy.refresh().ttl().toSeconds());
    }
}
