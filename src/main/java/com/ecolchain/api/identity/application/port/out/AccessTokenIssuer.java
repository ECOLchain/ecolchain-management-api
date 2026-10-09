package com.ecolchain.api.identity.application.port.out;

import com.ecolchain.api.identity.domain.Account;

public interface AccessTokenIssuer {
    record Issued(String token, long expiresInSeconds) {}
    Issued issue(Account account);
}
