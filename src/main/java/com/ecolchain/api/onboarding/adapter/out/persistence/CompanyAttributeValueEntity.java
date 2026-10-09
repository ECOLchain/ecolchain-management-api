package com.ecolchain.api.onboarding.adapter.out.persistence;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "company_attribute_value")
public class CompanyAttributeValueEntity extends PanacheEntityBase {
    @Id public UUID id;
    @Column(name = "company_id", nullable = false) public UUID companyId;
    @Column(name = "attribute_id", nullable = false) public UUID attributeId;
    @Column(name = "value_text") public String valueText;
    @Column(name = "updated_at", nullable = false) public Instant updatedAt;
}
