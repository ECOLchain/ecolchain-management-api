package com.ecolchain.api.catalog.adapter.out.persistence;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "step_attribute")
@IdClass(StepAttributeEntity.PK.class)
public class StepAttributeEntity extends PanacheEntityBase {
    @Id @Column(name = "step_id") public UUID stepId;
    @Id @Column(name = "attribute_id") public UUID attributeId;
    @Column(nullable = false) public boolean required;
    @Column(nullable = false) public int position;

    public static class PK implements Serializable {
        public UUID stepId;
        public UUID attributeId;

        @Override public boolean equals(Object o) {
            return o instanceof PK p && stepId.equals(p.stepId) && attributeId.equals(p.attributeId);
        }
        @Override public int hashCode() { return java.util.Objects.hash(stepId, attributeId); }
    }
}
