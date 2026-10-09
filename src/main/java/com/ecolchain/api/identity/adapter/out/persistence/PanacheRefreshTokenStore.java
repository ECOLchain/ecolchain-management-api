package com.ecolchain.api.identity.adapter.out.persistence;

import com.ecolchain.api.identity.application.port.out.RefreshTokenStore;
import com.ecolchain.api.identity.domain.RefreshToken;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class PanacheRefreshTokenStore implements RefreshTokenStore {

    @Override
    @Transactional
    public RefreshToken save(RefreshToken t) {
        RefreshTokenEntity e = t.id != null ? RefreshTokenEntity.findById(t.id) : null;
        if (e == null) {
            e = new RefreshTokenEntity();
            e.id = t.id == null ? UUID.randomUUID() : t.id;
            e.createdAt = Instant.now();
        }
        e.accountId = t.accountId;
        e.familyId = t.familyId;
        e.tokenHash = t.tokenHash;
        e.expiresAt = t.expiresAt;
        e.familyExpiresAt = t.familyExpiresAt;
        e.revokedAt = t.revokedAt;
        e.replacedBy = t.replacedBy;
        e.persist();
        t.id = e.id;
        return t;
    }

    @Override
    public Optional<RefreshToken> byHash(String hash) {
        return RefreshTokenEntity.<RefreshTokenEntity>find("tokenHash", hash)
                .firstResultOptional().map(this::toDomain);
    }

    @Override
    @Transactional
    public void revokeFamily(UUID familyId, UUID replacedBy) {
        RefreshTokenEntity.update("revokedAt = ?1, replacedBy = ?2 where familyId = ?3 and revokedAt is null",
                Instant.now(), replacedBy, familyId);
    }

    @Override
    public List<RefreshToken> activeFamily(UUID familyId) {
        return RefreshTokenEntity.<RefreshTokenEntity>find("familyId = ?1 and revokedAt is null", familyId)
                .list().stream().map(this::toDomain).toList();
    }

    private RefreshToken toDomain(RefreshTokenEntity e) {
        var t = new RefreshToken();
        t.id = e.id; t.accountId = e.accountId; t.familyId = e.familyId; t.tokenHash = e.tokenHash;
        t.expiresAt = e.expiresAt; t.familyExpiresAt = e.familyExpiresAt;
        t.revokedAt = e.revokedAt; t.replacedBy = e.replacedBy; t.createdAt = e.createdAt;
        return t;
    }
}
