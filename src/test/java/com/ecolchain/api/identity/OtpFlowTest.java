package com.ecolchain.api.identity;

import com.ecolchain.api.identity.application.port.out.AccountStore;
import com.ecolchain.api.identity.application.port.out.OtpStore;
import com.ecolchain.api.identity.application.port.out.RefreshTokenStore;
import com.ecolchain.api.identity.domain.Account;
import com.ecolchain.api.identity.domain.OtpCode;
import com.ecolchain.api.identity.domain.RefreshToken;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;

@QuarkusTest
class OtpFlowTest {

    @InjectMock OtpStore otps;
    @InjectMock AccountStore accounts;
    @InjectMock RefreshTokenStore refreshes;

    @BeforeEach
    void reset() {
        Mockito.reset(otps, accounts, refreshes);
        Mockito.when(otps.countLastHour(anyString())).thenReturn(0);
        Mockito.when(otps.countIpLastHour(any())).thenReturn(0);
        Mockito.when(otps.countGlobalSince(any())).thenReturn(0);
        Mockito.when(otps.lastRequestAt(anyString())).thenReturn(null);
    }

    @Test
    void requestOtpAlways202() {
        given().contentType("application/json")
                .body("{\"email\":\"pessoa@empresa.com\"}")
                .when().post("/auth/otp/request")
                .then().statusCode(202)
                .body("erros.size()", is(0));
    }

    @Test
    void requestOtpRateLimitedByCooldown() {
        Mockito.when(otps.lastRequestAt("pessoa@empresa.com"))
                .thenReturn(Instant.now().minusSeconds(10));
        given().contentType("application/json")
                .body("{\"email\":\"pessoa@empresa.com\"}")
                .when().post("/auth/otp/request")
                .then().statusCode(429)
                .body("erros[0].code", is("TOO_MANY_REQUESTS"));
    }

    @Test
    void verifyWrongCodeIs401AndConsumesAttempt() {
        var entry = new OtpStore.Entry(UUID.randomUUID(), "pessoa@empresa.com",
                OtpCode.hmac("111111", "ecolchain-mvp-dev-pepper"),
                Instant.now().plusSeconds(600), 0, null, null);
        Mockito.when(otps.latestPendingFor("pessoa@empresa.com")).thenReturn(Optional.of(entry));
        given().contentType("application/json")
                .body("{\"email\":\"pessoa@empresa.com\",\"codigo\":\"999999\"}")
                .when().post("/auth/otp/verify")
                .then().statusCode(401)
                .body("erros[0].code", is("OTP_INVALID"));
        Mockito.verify(otps).incrementAttempts(entry.id());
    }

    @Test
    void verifyRightCodeIssuesSession() {
        var entry = new OtpStore.Entry(UUID.randomUUID(), "dono@empresa.com",
                OtpCode.hmac("123456", "ecolchain-mvp-dev-pepper"),
                Instant.now().plusSeconds(600), 0, null, null);
        Mockito.when(otps.latestPendingFor("dono@empresa.com")).thenReturn(Optional.of(entry));
        Mockito.when(accounts.byEmail("dono@empresa.com")).thenReturn(Optional.empty());
        Mockito.when(accounts.save(any())).thenAnswer(inv -> {
            Account a = inv.getArgument(0);
            a.id = UUID.randomUUID();
            return a;
        });
        given().contentType("application/json")
                .body("{\"email\":\"dono@empresa.com\",\"codigo\":\"123456\"}")
                .when().post("/auth/otp/verify")
                .then().statusCode(200)
                .body("data.accessToken", notNullValue())
                .body("data.expiraEm", is(900))
                .body("data.conta.email", is("dono@empresa.com"))
                .body("data.conta.papel", is("COMPANY_OWNER"))
                .cookie("ecolchain_refresh", notNullValue());
        Mockito.verify(otps).consume(entry.id());
        Mockito.verify(refreshes).save(org.mockito.ArgumentMatchers.any(RefreshToken.class));
    }

    @Test
    void refreshReusedRevokesFamily() {
        var stale = new RefreshToken();
        stale.id = UUID.randomUUID();
        stale.accountId = UUID.randomUUID();
        stale.familyId = UUID.randomUUID();
        stale.revokedAt = Instant.now();
        stale.replacedBy = UUID.randomUUID();
        stale.expiresAt = Instant.now().plus(1, ChronoUnit.DAYS);
        stale.familyExpiresAt = Instant.now().plus(30, ChronoUnit.DAYS);
        String secret = "segredo-velho";
        Mockito.when(refreshes.byHash(RefreshToken.sha256(secret))).thenReturn(Optional.of(stale));
        given().cookie("ecolchain_refresh", stale.familyId + "." + secret)
                .when().post("/auth/refresh")
                .then().statusCode(401)
                .body("erros[0].code", is("REFRESH_REUSED"));
        Mockito.verify(refreshes).revokeFamily(stale.familyId, null);
    }

    @Test
    void refreshWithoutCookieIs401() {
        given().when().post("/auth/refresh")
                .then().statusCode(401)
                .body("erros[0].code", is("REFRESH_INVALID"));
    }
}
