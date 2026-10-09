package com.ecolchain.api.identity.adapter.out.persistence;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "email_otp")
public class EmailOtpEntity extends PanacheEntityBase {
    @Id public UUID id;
    @Column(nullable = false) public String email;
    @Column(name = "code_hmac", nullable = false) public String codeHmac;
    @Column(name = "expires_at", nullable = false) public Instant expiresAt;
    @Column(nullable = false) public int attempts;
    @Column(name = "consumed_at") public Instant consumedAt;
    @Column(name = "request_ip") public String requestIp;
    @Column(name = "created_at", nullable = false) public Instant createdAt;
}
