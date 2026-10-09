package com.ecolchain.api.identity.adapter.out.persistence;

import com.ecolchain.api.identity.application.port.out.AccountStore;
import com.ecolchain.api.identity.domain.Account;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class PanacheAccountStore implements AccountStore {

    @Override
    public Optional<Account> byEmail(String email) {
        return AccountEntity.<AccountEntity>find("email", email.toLowerCase()).firstResultOptional().map(this::toDomain);
    }

    @Override
    public Optional<Account> byId(UUID id) {
        return Optional.ofNullable(AccountEntity.findById(id)).map(e -> toDomain((AccountEntity) e));
    }

    @Override
    @Transactional
    public Account save(Account account) {
        AccountEntity e = account.id != null ? AccountEntity.findById(account.id) : null;
        if (e == null) {
            e = new AccountEntity();
            e.id = account.id == null ? UUID.randomUUID() : account.id;
            e.createdAt = Instant.now();
        }
        e.email = account.email.toLowerCase();
        e.fullName = account.fullName;
        e.role = account.role.name();
        e.status = account.status.name();
        e.updatedAt = Instant.now();
        e.persist();
        account.id = e.id;
        return account;
    }

    private Account toDomain(AccountEntity e) {
        var a = new Account();
        a.id = e.id; a.email = e.email; a.fullName = e.fullName;
        a.role = Account.Role.valueOf(e.role); a.status = Account.Status.valueOf(e.status);
        a.createdAt = e.createdAt; a.updatedAt = e.updatedAt;
        return a;
    }
}
