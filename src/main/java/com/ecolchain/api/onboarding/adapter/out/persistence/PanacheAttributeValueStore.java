package com.ecolchain.api.onboarding.adapter.out.persistence;

import com.ecolchain.api.onboarding.application.port.out.AttributeValueStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
public class PanacheAttributeValueStore implements AttributeValueStore {

    private static Value toValue(CompanyAttributeValueEntity e) {
        return new Value(e.attributeId, e.valueText, e.reviewStatus, e.reviewNote, e.updatedAt);
    }

    @Override
    public Optional<Value> find(UUID companyId, UUID attributeId) {
        return CompanyAttributeValueEntity.<CompanyAttributeValueEntity>find(
                        "companyId = ?1 and attributeId = ?2", companyId, attributeId)
                .firstResultOptional().map(PanacheAttributeValueStore::toValue);
    }

    @Override
    @Transactional
    public void upsert(UUID companyId, UUID attributeId, String valueText) {
        var e = CompanyAttributeValueEntity.<CompanyAttributeValueEntity>find(
                "companyId = ?1 and attributeId = ?2", companyId, attributeId).firstResult();
        if (e == null) {
            e = new CompanyAttributeValueEntity();
            e.id = UUID.randomUUID();
            e.companyId = companyId;
            e.attributeId = attributeId;
        }
        e.valueText = valueText;
        e.reviewStatus = "PENDING";
        e.reviewNote = null;
        e.updatedAt = Instant.now();
        e.persist();
    }

    @Override
    @Transactional
    public void setReview(UUID companyId, UUID attributeId, String reviewStatus, String reviewNote) {
        CompanyAttributeValueEntity.<CompanyAttributeValueEntity>find(
                        "companyId = ?1 and attributeId = ?2", companyId, attributeId)
                .firstResultOptional().ifPresent(e -> {
                    e.reviewStatus = reviewStatus;
                    e.reviewNote = reviewNote;
                    e.persist();
                });
    }

    @Override
    public Map<UUID, Value> all(UUID companyId) {
        return CompanyAttributeValueEntity.<CompanyAttributeValueEntity>find("companyId", companyId).list()
                .stream().collect(Collectors.toMap(e -> e.attributeId,
                        PanacheAttributeValueStore::toValue));
    }
}
