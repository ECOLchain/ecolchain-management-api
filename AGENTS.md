# AGENTS.md

Quarkus 3.40.1 / Java 25 — management API do ecolchain.
Infra (OCI) provisionada em repo separado: `../ecolchain-infra-general`, root `infra/terraform/management-api`.

## Comandos

- Build/testes: `./mvnw clean verify` — testes rodam 100% offline (profile `%test` sem DB, cloud ou Docker).
- Dev: `./mvnw quarkus:dev` — precisa de `.env` na raiz (copiar `.env.example` e preencher).
- Wrapper restaurado: Maven 3.9.12 (system `mvn` 3.10 também funciona).

## Integração OCI (concluída 2026-10-08)

- **ADB 23ai "ecolmgmt"**: one-way TLS, porta 1521, SEM wallet. Mesma JDBC URL para dev e prod — separação por schema: `MGMT_DEV` (dev) / `MGMT_PROD` (prod). URL em `application.properties` como `ecolchain.db.url`.
- **Criação dos schemas**: `db/bootstrap/V1__create_schema_users.sql` + profile Maven `db-bootstrap` (roda como ADMIN, secrets só via `-Dflyway.password=...` na linha de comando — nunca em arquivo). Histórico em `ADMIN.flyway_schema_history` (idempotente).
- **Migrations da app**: `src/main/resources/db/migration/`, aplicadas no startup como o schema user (`quarkus.flyway.migrate-at-start=true`). `V1__baseline` é no-op de propósito — domínio ainda não modelado.
- **Object Storage S3-compat**: endpoint e region `sa-saopaulo-1` em `application.properties`, path-style, credenciais estáticas via `.env` (`QUARKUS_S3_AWS_CREDENTIALS_STATIC_PROVIDER_*`). Buckets privados: `ecolchain-bucket-docs-dev` / `ecolchain-bucket-docs-prod`.
- **Presign temporário**: `DocumentStorageService` + `DevDocumentResource` (`@IfBuildProfile("dev")` — NÃO vai para prod). PUT assina `Content-Type`+`Content-Length`; key = `docs/{uuid}-{nome}`. Validado E2E: OCI enforça headers assinados (errado → 403) e responde preflight CORS (PUT + content-type, origin *).

## Regras de segurança

- Nunca commitar `.env` nem conteúdo de `~/.oci` (gitignored).
- Senhas MGMT_DEV/MGMT_PROD estão em `application.properties` por decisão explícita do time. Senha ADMIN **nunca** no repo nem no runtime da app.
- Prod é read-only para verificação: nunca subir a app com profile prod fora de deploy real, nunca escrever no bucket prod.

## Pendências conhecidas

- `jdbc_url_tls` do Terraform é vazio (`management-api-data` procura chave `TP_TLS` inexistente; o map só tem strings mTLS porta 1522). URL 1521 correta está hardcoded nos properties. Fix do output = PR separado no repo de infra.
- `oci` CLI local está configurado para outra tenancy (`grltxnor0awu` ≠ namespace dos buckets `grmykw8bmw9d`): `oci os object` não enxerga os docs buckets. Usar `aws s3api --endpoint-url <s3_endpoint>` com as credenciais do `.env`.
- `MGMT_PROD.flyway_schema_history` já existe com a row do baseline (boot acidental em 2026-10-08, inerte — a tabela seria criada no primeiro deploy de qualquer forma).
- ADB Always Free **auto-para** após ~7 dias sem atividade — falha de conexão no startup pode ser o banco parado (start no console OCI). ~30 sessions no total: pools pequenos (dev=5, prod=10).
- `terraform output -json` inclui `schema_bootstrap_sql` com senhas em plaintext — não colar a saída crua em logs/PRs.
- DevServices globais desligados (`quarkus.devservices.enabled=false`, Docker indisponível); observability DevService (LGTM) desligado em `%dev`.

## Próxima fase — API-first (direção definida)

1. Definir o contrato OpenAPI **primeiro** (`openapi/` ou similar).
2. Gerar interfaces via plugin do OpenAPI Generator.
3. Implementar as interfaces — `DevDocumentResource` é temporário e será substituído; upload-url provavelmente vira `POST` (cria intenção de upload).
4. Entidade `Document` (Panache) + migration real entram junto com o contrato.
5. Padrões de backend (pacotes, erro, auth) saem dessa fase — ver `docs/proposta-auth-onboarding.md`.
