package com.ecolchain.api.identity.application.port.out;

import com.ecolchain.api.identity.domain.RefreshToken;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenStore {
    RefreshToken save(RefreshToken token);
    Optional<RefreshToken> byHash(String hash);
    /** Revoga um único token (rotação normal). */
    void revoke(UUID tokenId, UUID replacedBy);
    /** Revoga todos os tokens ativos da família (reuso suspeito, logout). */
    void revokeFamily(UUID familyId, UUID replacedBy);
    List<RefreshToken> activeFamily(UUID familyId);
}
