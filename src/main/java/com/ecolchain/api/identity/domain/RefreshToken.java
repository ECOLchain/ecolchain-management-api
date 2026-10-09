package com.ecolchain.api.identity.domain;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/** Refresh token opaco de 256 bits, rotativo; persistido como SHA-256. */
public class RefreshToken {
    private static final SecureRandom RANDOM = new SecureRandom();

    public UUID id;
    public UUID accountId;
    public UUID familyId;
    public String tokenHash;
    public Instant expiresAt;
    public Instant familyExpiresAt;
    public Instant revokedAt;
    public UUID replacedBy;
    public Instant createdAt;

    public static String newSecret() {
        byte[] b = new byte[32];
        RANDOM.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    public static String sha256(String secret) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public boolean active(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now) && familyExpiresAt.isAfter(now);
    }
}
