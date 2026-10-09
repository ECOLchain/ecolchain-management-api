package com.ecolchain.api.identity.adapter.in.web;

import com.ecolchain.api.contract.api.AuthApi;
import com.ecolchain.api.contract.model.*;
import com.ecolchain.api.identity.application.AuthPolicy;
import com.ecolchain.api.identity.application.usecase.*;
import com.ecolchain.api.common.web.Envelopes;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.util.List;

/** Endpoints de autenticação (OTP, refresh, logout). docs/features/auth-otp.md, auth-sessao.md */
public class AuthController implements AuthApi {

    @Inject RequestOtpUseCase requestOtp;
    @Inject VerifyOtpUseCase verifyOtp;
    @Inject RefreshSessionUseCase refreshSession;
    @Inject LogoutUseCase logout;
    @Inject AuthPolicy policy;

    @Context HttpHeaders headers;
    @Context UriInfo uriInfo;

    private String clientIp() {
        var xff = headers.getHeaderString("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return null;
    }

    private NewCookie refreshCookie(String value, int maxAgeSeconds) {
        return new NewCookie.Builder(policy.refresh().cookieName())
                .value(value)
                .path("/")
                .httpOnly(true)
                .secure(policy.refresh().cookieSecure())
                .sameSite(NewCookie.SameSite.STRICT)
                .maxAge(maxAgeSeconds)
                .build();
    }

    private String cookieValue() {
        var cookie = headers.getCookies().get(policy.refresh().cookieName());
        return cookie == null ? null : cookie.getValue();
    }

    private static SessionResponse sessionBody(String token, long ttl,
                                               com.ecolchain.api.identity.domain.Account account) {
        var conta = new ContaInfo();
        conta.setEmail(account.email);
        conta.setPapel(AccountRole.valueOf(account.role.name()));
        conta.setNomeCompleto(account.fullName);
        var data = new SessionResponseAllOfData();
        data.setAccessToken(token);
        data.setExpiraEm(ttl);
        data.setConta(conta);
        var body = new SessionResponse();
        body.setData(data);
        body.setLinks(List.of());
        body.setErros(List.of());
        return body;
    }

    @Override
    public Response requestOtp(OtpRequestRequest req) {
        requestOtp.execute(req.getEmail(), clientIp());
        var body = new EmptyResponse();
        body.setLinks(List.of());
        body.setErros(List.of());
        return Response.status(202).entity(body).build();
    }

    @Override
    public Response verifyOtp(OtpVerifyRequest req) {
        var result = verifyOtp.execute(req.getEmail(), req.getCodigo());
        return Response.ok(sessionBody(result.accessToken(), result.accessTtlSeconds(), result.account()))
                .cookie(refreshCookie(result.refreshSecret(), (int) result.refreshTtlSeconds()))
                .build();
    }

    @Override
    public Response refreshSession() {
        var result = refreshSession.execute(cookieValue());
        var conta = new ContaInfo();
        var data = new SessionResponseAllOfData();
        data.setAccessToken(result.accessToken());
        data.setExpiraEm(result.accessTtlSeconds());
        var body = new SessionResponse();
        body.setData(data);
        body.setLinks(List.of());
        body.setErros(List.of());
        return Response.ok(body)
                .cookie(refreshCookie(result.refreshSecret(), (int) result.refreshTtlSeconds()))
                .build();
    }

    @Override
    public Response logout() {
        logout.execute(cookieValue());
        var body = new EmptyResponse();
        body.setLinks(List.of());
        body.setErros(List.of());
        return Response.ok(body)
                .cookie(refreshCookie("", 0))
                .build();
    }
}
