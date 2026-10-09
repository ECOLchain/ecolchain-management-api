package com.ecolchain.api.common.security;

import com.ecolchain.api.common.web.BusinessException;
import com.ecolchain.api.common.web.ErrorCodes;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;
import java.util.List;

/**
 * Deny-by-default: todo path fora da lista pública exige JWT válido.
 * Auth lazy (quarkus.http.auth.proactive=false) para os erros saírem
 * no envelope RFC 9457 em vez do challenge vazio do container.
 * docs/features/auth-sessao.md
 */
@Provider
@Priority(Priorities.AUTHENTICATION)
public class AuthFilter implements ContainerRequestFilter {

    private static final List<String> PUBLIC_PREFIXES = List.of(
            "/auth/", "/public/", "/.well-known/",
            "/q/health", "/q/openapi", "/q/swagger-ui",
            "/dev-mailbox", "/dev-ui");

    @Inject SecurityIdentity identity;

    @Override
    public void filter(ContainerRequestContext ctx) {
        String raw = ctx.getUriInfo().getPath();
        String path = raw.startsWith("/") ? raw : "/" + raw;
        boolean publicPath = PUBLIC_PREFIXES.stream().anyMatch(path::startsWith);
        if (!publicPath && identity.isAnonymous()) {
            throw new BusinessException(ErrorCodes.AUTH_REQUIRED, 401, "autenticação necessária");
        }
    }
}
