package com.ecolchain.api.identity.adapter.out.persistence;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_token")
public class RefreshTokenEntity extends PanacheEntityBase {
    @Id public UUID id;
    @Column(name = "account_id", nullable = false) public UUID accountId;
    @Column(name = "family_id", nullable = false) public UUID familyId;
    @Column(name = "token_hash", nullable = false) public String tokenHash;
    @Column(name = "expires_at", nullable = false) public Instant expiresAt;
    @Column(name = "family_expires_at", nullable = false) public Instant familyExpiresAt;
    @Column(name = "revoked_at") public Instant revokedAt;
    @Column(name = "replaced_by") public UUID replacedBy;
    @Column(name = "created_at", nullable = false) public Instant createdAt;
}
