package com.ecolchain.api.onboarding.adapter.out.persistence;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "company_member")
public class CompanyMemberEntity extends PanacheEntityBase {
    @Id public UUID id;
    @Column(name = "company_id", nullable = false) public UUID companyId;
    @Column(name = "account_id", nullable = false) public UUID accountId;
    @Column(nullable = false) public String role;
    @Column(name = "created_at", nullable = false) public Instant createdAt;
}
