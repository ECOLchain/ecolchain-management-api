package com.ecolchain.api.identity.domain;

import java.security.SecureRandom;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/** OTP de 6 dígitos; guardado como HMAC-SHA256(code, pepper) — nunca em claro. */
public final class OtpCode {
    private static final SecureRandom RANDOM = new SecureRandom();

    private OtpCode() {}

    public static String generate() {
        return "%06d".formatted(RANDOM.nextInt(1_000_000));
    }

    public static String hmac(String code, String pepper) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(pepper.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(code.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static boolean matches(String code, String expectedHmac, String pepper) {
        String actual = hmac(code, pepper);
        return java.security.MessageDigest.isEqual(
                actual.getBytes(StandardCharsets.UTF_8), expectedHmac.getBytes(StandardCharsets.UTF_8));
    }
}
