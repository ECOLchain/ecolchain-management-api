package com.ecolchain.api.identity.adapter.out.persistence;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "account")
public class AccountEntity extends PanacheEntityBase {
    @Id public UUID id;
    @Column(nullable = false) public String email;
    @Column(name = "full_name") public String fullName;
    @Column(name = "phone") public String phone;
    @Column(nullable = false) public String role;
    @Column(nullable = false) public String status;
    @Column(name = "created_at", nullable = false) public Instant createdAt;
    @Column(name = "updated_at", nullable = false) public Instant updatedAt;
}
