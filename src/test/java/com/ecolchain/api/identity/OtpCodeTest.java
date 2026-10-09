package com.ecolchain.api.identity;

import com.ecolchain.api.identity.domain.OtpCode;
import com.ecolchain.api.identity.domain.RefreshToken;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import static org.junit.jupiter.api.Assertions.*;

class OtpCodeTest {

    @Test
    void generatesSixDigits() {
        for (int i = 0; i < 100; i++) {
            assertTrue(OtpCode.generate().matches("[0-9]{6}"));
        }
    }

    @Test
    void hmacMatchesOnlyWithSamePepper() {
        String h = OtpCode.hmac("123456", "pepper-1");
        assertTrue(OtpCode.matches("123456", h, "pepper-1"));
        assertFalse(OtpCode.matches("123456", h, "pepper-2"));
        assertFalse(OtpCode.matches("654321", h, "pepper-1"));
    }

    @Test
    void refreshTokenActiveWindow() {
        var t = new RefreshToken();
        t.expiresAt = Instant.now().plus(1, ChronoUnit.HOURS);
        t.familyExpiresAt = Instant.now().plus(30, ChronoUnit.DAYS);
        assertTrue(t.active(Instant.now()));
        t.revokedAt = Instant.now();
        assertFalse(t.active(Instant.now()));
        t.revokedAt = null;
        t.expiresAt = Instant.now().minusSeconds(1);
        assertFalse(t.active(Instant.now()));
    }

    @Test
    void refreshSecretIsLongEnough() {
        String s = RefreshToken.newSecret();
        assertTrue(s.length() >= 40);
        assertNotEquals(RefreshToken.sha256(s), RefreshToken.sha256(s + "x"));
    }
}
