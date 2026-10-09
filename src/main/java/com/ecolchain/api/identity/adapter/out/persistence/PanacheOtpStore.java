package com.ecolchain.api.identity.adapter.out.persistence;

import com.ecolchain.api.identity.application.port.out.OtpStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class PanacheOtpStore implements OtpStore {

    @Override
    @Transactional
    public void insert(Entry entry) {
        var e = new EmailOtpEntity();
        e.id = entry.id();
        e.email = entry.email();
        e.codeHmac = entry.codeHmac();
        e.expiresAt = entry.expiresAt();
        e.attempts = entry.attempts();
        e.consumedAt = entry.consumedAt();
        e.requestIp = entry.requestIp();
        e.createdAt = Instant.now();
        e.persist();
    }

    @Override
    public Optional<Entry> latestPendingFor(String email) {
        return EmailOtpEntity.<EmailOtpEntity>find(
                        "email = ?1 and consumedAt is null order by createdAt desc", email)
                .firstResultOptional().map(this::toEntry);
    }

    @Override
    @Transactional
    public void incrementAttempts(UUID id) {
        EmailOtpEntity.update("attempts = attempts + 1 where id = ?1", id);
    }

    @Override
    @Transactional
    public void consume(UUID id) {
        EmailOtpEntity.update("consumedAt = ?1 where id = ?2", Instant.now(), id);
    }

    @Override
    public int countLastHour(String email) {
        return (int) EmailOtpEntity.count("email = ?1 and createdAt > ?2", email, Instant.now().minusSeconds(3600));
    }

    @Override
    public int countIpLastHour(String ip) {
        if (ip == null) return 0;
        return (int) EmailOtpEntity.count("requestIp = ?1 and createdAt > ?2", ip, Instant.now().minusSeconds(3600));
    }

    @Override
    public int countGlobalSince(Instant since) {
        return (int) EmailOtpEntity.count("createdAt > ?1", since);
    }

    @Override
    public Instant lastRequestAt(String email) {
        return EmailOtpEntity.<EmailOtpEntity>find("email = ?1 order by createdAt desc", email)
                .firstResultOptional().map(e -> e.createdAt).orElse(null);
    }

    private Entry toEntry(EmailOtpEntity e) {
        return new Entry(e.id, e.email, e.codeHmac, e.expiresAt, e.attempts, e.consumedAt, e.requestIp);
    }
}
