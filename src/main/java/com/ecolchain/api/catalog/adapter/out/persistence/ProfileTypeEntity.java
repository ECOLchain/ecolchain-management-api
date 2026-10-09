package com.ecolchain.api.catalog.adapter.out.persistence;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "profile_type")
public class ProfileTypeEntity extends PanacheEntityBase {
    @Id public UUID id;
    @Column(nullable = false) public String code;
    @Column(name = "name_pt", nullable = false) public String namePt;
    @Column(name = "name_en", nullable = false) public String nameEn;
    @Column(name = "desc_pt") public String descPt;
    @Column(name = "desc_en") public String descEn;
    @Column(nullable = false) public boolean active;
    @Column(nullable = false) public int position;
    @Column(name = "created_at", nullable = false) public Instant createdAt;
    @Column(name = "updated_at", nullable = false) public Instant updatedAt;
}
