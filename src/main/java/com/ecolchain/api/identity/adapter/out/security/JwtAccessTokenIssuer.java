package com.ecolchain.api.identity.adapter.out.security;

import com.ecolchain.api.identity.application.port.out.AccessTokenIssuer;
import com.ecolchain.api.identity.domain.Account;
import io.smallrye.jwt.build.Jwt;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;

/** Emite o access token RS256 (15min): iss/aud/sub/groups/jti + kid no header. */
@ApplicationScoped
public class JwtAccessTokenIssuer implements AccessTokenIssuer {

    @ConfigProperty(name = "smallrye.jwt.new-token.lifespan", defaultValue = "900")
    long lifespan;

    @Override
    public Issued issue(Account account) {
        String token = Jwt.issuer("ecolchain-management-api")
                .audience("ecolchain-app")
                .subject(account.id.toString())
                .groups(Set.of(account.role.name()))
                .claim("email", account.email)
                .claim("jti", UUID.randomUUID().toString())
                .expiresIn(Duration.ofSeconds(lifespan))
                .sign();
        return new Issued(token, lifespan);
    }
}
