package com.ecolchain.api.identity.application.port.out;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface OtpStore {
    record Entry(UUID id, String email, String codeHmac, Instant expiresAt,
                 int attempts, Instant consumedAt, String requestIp) {}

    void insert(Entry entry);
    Optional<Entry> latestPendingFor(String email);
    void incrementAttempts(UUID id);
    void consume(UUID id);
    int countLastHour(String email);
    int countIpLastHour(String ip);
    int countGlobalSince(Instant since);
    Instant lastRequestAt(String email);
}
