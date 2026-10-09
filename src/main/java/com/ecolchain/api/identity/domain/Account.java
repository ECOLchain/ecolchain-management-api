package com.ecolchain.api.identity.domain;

import java.time.Instant;
import java.util.UUID;

public class Account {
    public enum Role { COMPANY_OWNER, PLATFORM_ADMIN }
    public enum Status { EMAIL_VERIFIED, ACTIVE, BLOCKED }

    public UUID id;
    public String email;
    public String fullName;
    public Role role;
    public Status status;
    public Instant createdAt;
    public Instant updatedAt;

    public Account() {}

    public static Account verified(String email, Role role) {
        var a = new Account();
        a.id = UUID.randomUUID();
        a.email = email;
        a.role = role;
        a.status = Status.EMAIL_VERIFIED;
        return a;
    }

    public boolean blocked() { return status == Status.BLOCKED; }
}
