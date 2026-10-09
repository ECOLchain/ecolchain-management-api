# Relato de implementação — auth + onboarding (MVP)

Data: 2026-10-09 · Branch: `feat/auth-onboarding` · Status: **completo e verificado E2E contra MGMT_DEV (ADB 23ai) + bucket `ecolchain-bucket-docs-dev`**.

## O que foi entregue

| Fatia | Conteúdo |
|---|---|
| Base | Contrato OpenAPI `openapi/management-api.yaml` (fonte da verdade, interfaces geradas via openapi-generator jaxrs-spec + build-helper add-source), envelope `{data, links, erros}` com Problem Details RFC 9457 + `code` + `campo`, `GlobalExceptionMappers`, `BusinessException`, `RequestLocale`, deny-by-default `AuthFilter` (auth proativo desligado para envelope funcionar). |
| Auth | OTP de e-mail 6 dígitos (HMAC-SHA256 com pepper, 10 min, 5 tentativas, cooldown 60 s, 5/h email, 20/h IP, 180/dia global), JWT RS256 15 min (`iss=ecolchain-management-api`, `aud=ecolchain-app`, JWKS público em `/.well-known/jwks.json`), refresh opaco rotativo em cookie `ecolchain_refresh` (HttpOnly, Secure, SameSite=Strict, Path=/auth, 7 d, família 30 d, reuso revoga a família com `REFRESH_REUSED`), logout, `/me` com `proximoPasso` calculado no servidor. |
| Cadastro | `PUT /me/company`: CNPJ alfanumérico novo formato (módulo 11 ASCII−48), razão social, nome completo, 1..N tipos de perfil, declaração + versão de termos. Unicidade de CNPJ por status (rejected libera recadastro). |
| Onboarding | Schema dinâmico = união dos fluxos dos tipos escolhidos (required vence optional), `GET /me/onboarding` com passos/atributos/valores/documentos/progresso, `PUT` por atributo com validação por `rules` (regex/maxLength), `POST /me/onboarding/submit` que bloqueia com `SUBMIT_INCOMPLETE` listando pendências. |
| Documentos | `POST .../upload-url` (presign PUT direto no bucket, key `docs/{uuid}-{nome}`, allow-list ext→contentType, Content-Type+Content-Length assinados), `confirm` (headObject + transição UPLOADED), `download-url` owner e admin. |
| Revisão | `GET /admin/companies` (filtro por status, paginação), `GET /admin/companies/{id}` (valores + documentos + download-url por item), `POST .../review` (APPROVE/REQUEST_CHANGES/REJECT/SUSPEND com nota obrigatória quando exigido, itens ACCEPT/REJECT por atributo), `GET /admin/audit-log`. `/admin/**` re-checa `role == PLATFORM_ADMIN` no banco a cada request. |
| Catálogo admin | CRUD de tipos de perfil + atributos + fluxo (`PUT /admin/profile-types/{code}/flow`); marcar atributo obrigatório em tipo já aprovado move empresas APPROVED → PENDING_UPDATE. |
| Seeds | 6 tipos de perfil (GERADOR/TRANSPORTADOR/COOPERATIVA/CLEANTECH/RECICLADORA/HIGIENIZADORA), ~36 atributos, 14 passos e vínculos conforme Anexo D; admin `leandroluz201616@gmail.com` como PLATFORM_ADMIN via migration V2. |

## Verificação E2E (real, em MGMT_DEV + bucket dev)

Fluxo exercido via curl contra `mvn quarkus:dev` (`QUARKUS_MAILER_MOCK=true`):

1. `POST /auth/otp/request` → 202 (genérico, sem enumeração); OTP visível em `/dev-mailbox`.
2. `POST /auth/otp/verify` → access token + cookie de refresh.
3. `POST /auth/refresh` → rotação; reuso de cookie antigo → `REFRESH_REUSED` + família revogada (verificado ao vivo).
4. Cooldown 60 s e teto 5/h por e-mail → 429 `TOO_MANY_REQUESTS` (verificado).
5. `PUT /me/company` → PENDING_DOCUMENTS; CNPJ inválido → `CNPJ_INVALID`.
6. `GET /me/onboarding` → união GERADOR+TRANSPORTADOR (3 passos); `responsible_cpf` com valor fora do regex → `ATTRIBUTE_VALUE_INVALID`; submit antes de completar → `SUBMIT_INCOMPLETE` com a lista de pendências.
7. Empresa 2 (CLEANTECH): `upload-url` → `PUT` no bucket → `confirm` (4 docs obrigatórios); PUT com `Content-Length` divergente → **403 do OCI** (assinatura enforçada, como esperado).
8. `POST /me/onboarding/submit` → UNDER_REVIEW; `/me` → `proximoPasso=AWAITING_REVIEW`.
9. Admin (OTP de `leandroluz201616@gmail.com`): lista, detalhe, `download-url` presignado funcionando, `review APPROVE` → APPROVED; `/me` do owner → `proximoPasso=HOME`.
10. `GET /admin/audit-log` → `COMPANY_SUBMITTED`, `COMPANY_REVIEW_APPROVE` registrados.

`./mvnw clean verify`: **40 testes, 0 falhas** (offline, profile `%test` sem DB/cloud).

## Decisões e desvios documentados

- **UUID como `RAW(16)`** em todas as colunas id/FK (exceto `audit_log.entity_id`, que é referência polimórfica em texto `VARCHAR2(36)`). Motivo: `ResultSet.getObject(UUID.class)` não é suportado pelo driver Oracle, e em VARCHAR2 os bytes ASCII prefixados iguais dos seeds faziam o persistence context colapsar entidades distintas na primeira linha. Seeds usam `HEXTORAW(REPLACE('...','-',''))`.
- **`InstantUtcConverter`** (`@Converter(autoApply=true)`, `common/persistence`): Hibernate ORM 7 mapeia `Instant` como TIMESTAMP_UTC e lê via `OffsetDateTime`, que falha com `ORA-18716` em coluna `TIMESTAMP` simples. O converter persiste `Instant` como `java.sql.Timestamp` (UTC) — única alteração necessária, sem tocar domínio nem entidades.
- **`DEFAULT x NOT NULL`** (ordem Oracle) em todas as migrations; removido índice `ix_account_email` redundante com o UNIQUE `uq_account_email` (ORA-01408).
- **Sem `DevDocumentResource`** — removido junto com seu teste; o endpoint real cobre o mesmo caminho com auth e entidade `document`.
- **Erros de login sempre genéricos** (`OTP_INVALID`), verify retorna 401; request sempre 202.
- **`proactive=false` + AuthFilter** — necessário para respostas 401/403 saírem no envelope (o desafio padrão do Quarkus devolve corpo vazio).
- Refresh cookie `Path=/auth` e `Secure`; em dev HTTP o cookie é enviado normalmente para localhost (verificado com curl jar).

## Como rodar

```bash
cp .env.example .env   # preencher S3 + mailer
JAVA_HOME=~/jdk25 ./mvnw quarkus:dev
# smoke: curl -X POST localhost:8080/auth/otp/request -d '{"email":"voce@dominio"}' -H 'Content-Type: application/json'
# código visível em http://localhost:8080/dev-mailbox?to=voce@dominio (mock mailer)
```

Coleção Bruno em `bruno/` cobre os 25 endpoints (env `local` com `{{baseUrl}}`, `{{token}}`, `{{email}}`).

## Pendências / próximos passos sugeridos

- Frontend SPA (`ecolchain-management-web`) — sessão filha implementa o fluxo completo.
- Texto legal dos Termos e arte final do e-mail OTP (ver `DUVIDAS.md`).
- `app.cnpj.lookup.enabled` desligado até escolher provedor de consulta CNPJ.
- Subir com profile `prod` exige deploy real (AGENTS.md: prod é read-only).
- HV000271: warnings de `@Valid` em container vindos das interfaces geradas — cosmético, sem impacto.

## Correções pós-E2E do frontend (2026-10-09)

Bugs encontrados pela sessão do SPA (`ecolchain-management-web`) no E2E de browser:

1. **`GET /me/onboarding` 500 com documento enviado** — `Revisao.status` no contrato tinha `[PENDING, ACCEPTED, REJECTED]` e o domínio emite `UPLOADED`. Contrato corrigido para `[PENDING, UPLOADED, ACCEPTED, REJECTED]`; `documento.status` (detalhe admin) alinhado a `[UPLOADED, ACCEPTED, REJECTED]`. `fromValue` agora mapeia 1:1.
2. **Refresh morria na 2ª rotação** — `revokeFamily` revogava todos os ativos da família, incluindo o token recém-criado. Criado `RefreshTokenStore.revoke(tokenId, replacedBy)` (revoga só o apresentado); `revokeFamily` fica exclusivo para reuso suspeito e logout. Verificado: 3 rotações seguidas 200; cookie velho re-usado ainda dispara `REFRESH_REUSED`.
3. **`expiraEm` é TTL, não epoch** — descrição do contrato corrigida ("TTL (segundos)"); frontend já normalizava.

`./mvnw clean verify`: 41 testes, 0 falhas.
