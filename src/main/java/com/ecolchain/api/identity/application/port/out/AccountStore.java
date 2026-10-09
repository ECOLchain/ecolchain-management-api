package com.ecolchain.api.identity.application.port.out;

import com.ecolchain.api.identity.domain.Account;
import java.util.Optional;
import java.util.UUID;

public interface AccountStore {
    Optional<Account> byEmail(String email);
    Optional<Account> byId(UUID id);
    Account save(Account account);
}
