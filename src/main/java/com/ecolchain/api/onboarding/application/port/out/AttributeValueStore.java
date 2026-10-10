package com.ecolchain.api.onboarding.application.port.out;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface AttributeValueStore {
    record Value(UUID attributeId, String valueText, String reviewStatus, String reviewNote,
                 Instant updatedAt) {}
    Optional<Value> find(UUID companyId, UUID attributeId);
    /** Grava o valor e devolve o item ao estado PENDING de revisão (novo ciclo de análise). */
    void upsert(UUID companyId, UUID attributeId, String valueText);
    /** Revisão por item (admin): status ACCEPTED | REJECTED + nota opcional. */
    void setReview(UUID companyId, UUID attributeId, String reviewStatus, String reviewNote);
    Map<UUID, Value> all(UUID companyId);
}
