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

    @Override
    public Optional<Value> find(UUID companyId, UUID attributeId) {
        return CompanyAttributeValueEntity.<CompanyAttributeValueEntity>find(
                        "companyId = ?1 and attributeId = ?2", companyId, attributeId)
                .firstResultOptional().map(e -> new Value(e.attributeId, e.valueText, e.updatedAt));
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
        e.updatedAt = Instant.now();
        e.persist();
    }

    @Override
    public Map<UUID, Value> all(UUID companyId) {
        return CompanyAttributeValueEntity.<CompanyAttributeValueEntity>find("companyId", companyId).list()
                .stream().collect(Collectors.toMap(e -> e.attributeId,
                        e -> new Value(e.attributeId, e.valueText, e.updatedAt)));
    }
}
