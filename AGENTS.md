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

## Arquitetura e padrões de código (decisões do dono)

Detalhes e justificativas em `docs/proposta-auth-onboarding.md`.

- **API-first**: o contrato OpenAPI (`openapi/management-api.yaml`) é escrito primeiro; as interfaces JAX-RS são geradas pelo OpenAPI Generator (plugin Maven); os controllers implementam as interfaces geradas. Nunca editar código gerado.
- **Arquitetura hexagonal**: `domain` (regras, sem framework) / `application` (use cases + ports `in`/`out`) / `adapter` (`in.web` = controllers REST, `out.persistence` = Panache, `out.storage` = S3, `out.mail` = SMTP). Controller só traduz HTTP↔use case; domínio não conhece Quarkus.
- **Clean Code e SOLID estritos**; sem gambiarras.
- **Idiomas**: código em inglês; campos do contrato (request e response) em **pt-BR camelCase** (ex.: `razaoSocial`, `nomeCompleto`). Paths dos endpoints em inglês.
- **Sem `*Dto`**: payloads são `<Nome>Request` / `<Nome>Response`, em pacotes `request`/`response` da feature.
- **Envelope obrigatório** em toda resposta: `{ "data": {} | null, "links": [], "erros": [] }`. Cada item de `erros` é um Problem Details **RFC 9457** (`type`, `title`, `status`, `detail`, `instance`) + extensões `code` (estável, ex.: `CNPJ_IN_USE`) e `campo` (opcional). Mapeamento centralizado via `ExceptionMapper`/`@ServerExceptionMapper` (equivalente ao `@ControllerAdvice` do Spring). Status HTTP continua semântico (202, 400, 401...).
- **Doc por use case**: cada feature tem `docs/features/<nome>.md` descrevendo o que faz e como funciona; a classe/método referencia o arquivo no Javadoc. Mudou o código → atualiza o doc (de/para obrigatório).
- **Dinamismo**: regra de negócio configurável fica no banco (catálogo/fluxos), nunca hardcoded.

## Auth e onboarding (decisões)

- Login por OTP de e-mail (sem senha) para empresas e admin; JWT RS256 15 min + refresh token rotativo (cookie HttpOnly).
- Admin inicial: `leandroluz201616@gmail.com` semeado por migration; novos admins via INSERT no banco por ora.
- Empresa pode ter **N tipos de perfil** (`COMPANY_PROFILE`); o onboarding é a união dos fluxos dos tipos escolhidos.
- Chaves JWT de dev e prod **commitadas** em `keys/` (decisão explícita do dono — é MVP). `.gitignore` abre exceção para `keys/*.pem`; `ConfigGuardTest` faz allow-list dessa pasta e continua bloqueando outros segredos. Débito técnico registrado: mover para secret/OCI Vault na primeira revisão de segurança.

## Próxima fase — execução

1. Escrever o contrato OpenAPI (ver `docs/proposta-auth-onboarding.md`, anexo B).
2. Configurar o plugin do OpenAPI Generator e gerar as interfaces.
3. Implementar os controllers — `DevDocumentResource` é temporário e será substituído; upload-url é `POST` (cria intenção de upload, entidade `DOCUMENT`).
4. Migrations Flyway: identidade, catálogo, empresa, documento, auditoria + seeds (6 tipos de perfil com a matriz de atributos do anexo D da proposta, admin inicial).
