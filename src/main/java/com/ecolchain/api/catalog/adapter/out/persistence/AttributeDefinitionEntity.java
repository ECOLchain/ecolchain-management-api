package com.ecolchain.api.catalog.adapter.out.persistence;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "attribute_definition")
public class AttributeDefinitionEntity extends PanacheEntityBase {
    @Id public UUID id;
    @Column(nullable = false) public String code;
    @Column(nullable = false) public String kind;
    @Column(name = "label_pt", nullable = false) public String labelPt;
    @Column(name = "label_en", nullable = false) public String labelEn;
    @Column(name = "help_pt") public String helpPt;
    @Column(name = "help_en") public String helpEn;
    @Column public String rules;
    @Column(nullable = false) public boolean active;
    @Column(name = "created_at", nullable = false) public Instant createdAt;
    @Column(name = "updated_at", nullable = false) public Instant updatedAt;
}
