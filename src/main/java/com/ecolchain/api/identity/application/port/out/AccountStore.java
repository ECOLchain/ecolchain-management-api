package com.ecolchain.api.identity.application.port.out;

import com.ecolchain.api.identity.domain.Account;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountStore {
    Optional<Account> byEmail(String email);
    Optional<Account> byId(UUID id);
    List<Account> listByRole(Account.Role role);
    Account save(Account account);
}
