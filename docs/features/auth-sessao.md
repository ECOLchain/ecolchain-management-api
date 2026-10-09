# Sessão: JWT RS256 + refresh rotativo

- Access: RS256 15min, claims `iss`/`aud`/`sub`(account UUID)/`groups`/`jti` + `kid` no header. Emissor `ecolchain-management-api`, audiência `ecolchain-app`.
- Refresh: opaco 256 bits, **persistido como SHA-256**, TTL 7d, família 30d. Cookie `ecolchain_refresh` HttpOnly Secure SameSite=Strict, valor `<familyId>.<secret>`.
- `POST /auth/refresh`: rotação — token velho revogado (`revoked_at`+`replaced_by`), novo emitido. **Reuso** (apresentar token já rotacionado) revoga a família inteira → `REFRESH_REUSED` (detecção de roubo).
- `POST /auth/logout`: revoga a família do cookie + limpa cookie (idempotente).
- `GET /.well-known/jwks.json`: JWKS público (n/e, kid).
- Chaves em `keys/` (dev e prod commitadas — decisão do dono, débito técnico documentado). Gerar novas: `./scripts/gen-jwt-keys.sh`.

## Deny-by-default
- `quarkus.http.auth.proactive=false` + `AuthFilter` (`common/security`): todo path fora dos prefixos públicos (`/auth/`, `/public/`, `/.well-known/`, `/q/`, `/dev-mailbox`) exige Bearer válido — respostas saem no envelope RFC 9457 (`AUTH_REQUIRED`), não no challenge vazio do container.
- `CurrentAccount` resolve `sub` → `Account` (404/bloqueada → erro). `/admin/**` re-checado no banco a cada request (`AdminController.admin()`), independente do grupo no token.
