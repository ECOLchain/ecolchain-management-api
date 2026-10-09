package com.ecolchain.api.catalog.adapter.out.persistence;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "onboarding_step")
public class OnboardingStepEntity extends PanacheEntityBase {
    @Id public UUID id;
    @Column(name = "profile_type_id", nullable = false) public UUID profileTypeId;
    @Column(nullable = false) public String code;
    @Column(name = "title_pt", nullable = false) public String titlePt;
    @Column(name = "title_en", nullable = false) public String titleEn;
    @Column(nullable = false) public int position;
}
