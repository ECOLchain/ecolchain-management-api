package com.ecolchain.api.onboarding.adapter.out.persistence;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "company")
public class CompanyEntity extends PanacheEntityBase {
    @Id public UUID id;
    @Column(nullable = false, length = 14) public String cnpj;
    @Column(name = "razao_social", nullable = false) public String razaoSocial;
    @Column(name = "wallet") public String wallet;
    @Column(nullable = false) public String status;
    @Column(name = "review_notes") public String reviewNotes;
    @Column(name = "terms_version") public String termsVersion;
    @Column(name = "terms_accepted_at") public Instant termsAcceptedAt;
    @Column(name = "created_at", nullable = false) public Instant createdAt;
    @Column(name = "updated_at", nullable = false) public Instant updatedAt;
    @Column(name = "submitted_at") public Instant submittedAt;
}
