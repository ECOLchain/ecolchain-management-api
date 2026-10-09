package com.ecolchain.api.identity.application;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;
import java.time.Duration;

@ConfigMapping(prefix = "app.auth")
public interface AuthPolicy {
    Otp otp();
    Refresh refresh();
    @WithName("terms-version") String termsVersion();

    interface Otp {
        Duration ttl();
        @WithName("max-attempts") int maxAttempts();
        @WithName("resend-cooldown") Duration resendCooldown();
        @WithName("max-per-hour") int maxPerHour();
        @WithName("ip-max-per-hour") int ipMaxPerHour();
        @WithName("daily-global-cap") int dailyGlobalCap();
        String pepper();
    }

    interface Refresh {
        Duration ttl();
        @WithName("family-ttl") Duration familyTtl();
        @WithName("cookie-name") String cookieName();
        @WithName("cookie-secure") boolean cookieSecure();
    }
}
