package com.ecolchain.api.onboarding.adapter.out.persistence;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_log")
public class AuditLogEntity extends PanacheEntityBase {
    @Id public UUID id;
    @Column(name = "actor_id") public UUID actorId;
    @Column(name = "actor_email") public String actorEmail;
    @Column(nullable = false) public String action;
    @Column(name = "entity_type") public String entityType;
    @Column(name = "entity_id") public String entityId;
    @Column public String detail;
    @Column(name = "created_at", nullable = false) public Instant createdAt;
}
