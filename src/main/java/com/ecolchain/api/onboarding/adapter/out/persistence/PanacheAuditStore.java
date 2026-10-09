package com.ecolchain.api.onboarding.adapter.out.persistence;

import com.ecolchain.api.onboarding.application.port.out.AuditStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class PanacheAuditStore implements AuditStore {

    @Override
    @Transactional
    public void log(UUID actorId, String actorEmail, String action,
                    String entityType, String entityId, String detail) {
        var e = new AuditLogEntity();
        e.id = UUID.randomUUID();
        e.actorId = actorId;
        e.actorEmail = actorEmail;
        e.action = action;
        e.entityType = entityType;
        e.entityId = entityId;
        e.detail = detail;
        e.createdAt = Instant.now();
        e.persist();
    }

    @Override
    public List<Entry> list(int page, int size) {
        return AuditLogEntity.<AuditLogEntity>find("order by createdAt desc")
                .page(page, size).list().stream()
                .map(e -> new Entry(e.id, e.actorEmail, e.action, e.entityType, e.entityId, e.detail, e.createdAt))
                .toList();
    }
}
