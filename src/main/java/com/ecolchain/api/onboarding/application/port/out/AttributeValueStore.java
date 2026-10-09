package com.ecolchain.api.onboarding.application.port.out;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface AttributeValueStore {
    record Value(UUID attributeId, String valueText, Instant updatedAt) {}
    Optional<Value> find(UUID companyId, UUID attributeId);
    void upsert(UUID companyId, UUID attributeId, String valueText);
    Map<UUID, Value> all(UUID companyId);
}
