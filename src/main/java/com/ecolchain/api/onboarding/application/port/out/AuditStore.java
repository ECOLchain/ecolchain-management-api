package com.ecolchain.api.onboarding.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AuditStore {
    record Entry(UUID id, String actorEmail, String action, String entityType,
                 String entityId, String detail, Instant createdAt) {}
    void log(UUID actorId, String actorEmail, String action,
             String entityType, String entityId, String detail);
    List<Entry> list(int page, int size);
}
