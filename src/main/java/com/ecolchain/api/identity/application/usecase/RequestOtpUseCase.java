package com.ecolchain.api.identity.application.usecase;

import com.ecolchain.api.common.i18n.RequestLocale;
import com.ecolchain.api.common.web.BusinessException;
import com.ecolchain.api.common.web.ErrorCodes;
import com.ecolchain.api.identity.application.AuthPolicy;
import com.ecolchain.api.identity.application.port.out.OtpMailer;
import com.ecolchain.api.identity.application.port.out.OtpStore;
import com.ecolchain.api.identity.domain.OtpCode;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Solicita OTP: resposta genérica sempre (não revela se conta existe).
 * Limites: cooldown 60s, 5/h por email, teto por IP e cap diário global.
 * docs/features/auth-otp.md
 */
@ApplicationScoped
public class RequestOtpUseCase {

    @Inject AuthPolicy policy;
    @Inject OtpStore otps;
    @Inject OtpMailer mailer;
    @Inject RequestLocale locale;

    @Transactional
    public void execute(String rawEmail, String ip) {
        String email = rawEmail == null ? "" : rawEmail.trim().toLowerCase();
        if (email.isBlank() || !email.contains("@")) {
            throw new BusinessException(ErrorCodes.VALIDATION, 400, "e-mail inválido", "email");
        }
        Instant now = Instant.now();
        Instant last = otps.lastRequestAt(email);
        if (last != null && last.plus(policy.otp().resendCooldown()).isAfter(now)) {
            throw new BusinessException(ErrorCodes.TOO_MANY_REQUESTS, 429,
                    "aguarde antes de pedir um novo código");
        }
        if (otps.countLastHour(email) >= policy.otp().maxPerHour()
                || otps.countIpLastHour(ip) >= policy.otp().ipMaxPerHour()
                || otps.countGlobalSince(now.truncatedTo(ChronoUnit.DAYS)) >= policy.otp().dailyGlobalCap()) {
            throw new BusinessException(ErrorCodes.OTP_RATE_LIMITED, 429,
                    "limite de códigos atingido; tente mais tarde");
        }
        String code = OtpCode.generate();
        otps.insert(new OtpStore.Entry(UUID.randomUUID(), email,
                OtpCode.hmac(code, policy.otp().pepper()),
                now.plus(policy.otp().ttl()), 0, null, ip));
        mailer.send(email, code, locale.lang());
    }
}
