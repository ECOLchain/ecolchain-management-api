# PROMPT — sessão de implementação (auth + onboarding MVP)

> Como usar: abra uma sessão nova do Devin no diretório `/Users/leandro/Dev/ecolchain/ecolchain-management-api` e cole o conteúdo da seção "PROMPT" abaixo (tudo entre as linhas `---`).

---

Você vai implementar, do início ao fim e testado, o MVP de autenticação e onboarding da plataforma Ecolchain neste repositório (`ecolchain-management-api`, Quarkus 3.40.1, Java 25, Maven wrapper). O usuário está indisponível: **não faça nenhuma pergunta a ele** — dúvidas vão para `docs/DUVIDAS.md` (protocolo abaixo).

## Leitura obrigatória antes de codar

Nesta ordem, por inteiro:

1. `AGENTS.md` — comandos, integração OCI, regras de código invioláveis.
2. `docs/proposta-auth-onboarding.md` — decisões D1–D13, diagramas C4, fluxos (seção 5), modelo de dados (Anexo A), endpoints (Anexo B), segurança/chaves/e-mail (Anexo C), matriz de atributos por perfil que vira seed (Anexo D).
3. `docs/handoff-auth-onboarding.md` — ambiente, plano de slices e guardrails.
4. `src/main/java/com/ecolchain/api/` e `src/test/java/` — código existente (`DocumentStorageService`, `DocumentsConfig`, `DevDocumentResource`, `ConfigGuardTest`) para reaproveitar e seguir as convenções.
5. `src/main/resources/application.properties` e `pom.xml`.

## Contexto essencial

- **Stack**: Quarkus REST + Jackson, Hibernate Panache, Flyway (`migrate-at-start`), Oracle ADB 23ai (schemas `MGMT_DEV`/`MGMT_PROD`), S3-compat do OCI Object Storage, SmallRye JWT/OpenAPI/Health.
- **O que se quer**: login sem senha (e-mail + código OTP de 6 dígitos via OCI Email Delivery) → JWT RS256 (15 min) + refresh rotativo (cookie HttpOnly, 7 dias, família 30 dias, reuso revoga) → cadastro da empresa (CNPJ, razão social, nome completo, 1..N tipos de perfil) → onboarding dinâmico (dados + documentos por perfil, servidos como schema para o SPA) → upload por presign direto no bucket privado (só a `objectKey` no banco) → revisão manual do admin → `APPROVED`.
- **Admin**: `leandroluz201616@gmail.com` semeado por migration como `PLATFORM_ADMIN`; login é o mesmo OTP. Novos admins via INSERT por ora.
- **Tipos de perfil (seed, Anexo D da proposta)**: GERADOR, TRANSPORTADOR, COOPERATIVA, CLEANTECH, RECICLADORA, HIGIENIZADORA — cada um com seus passos e atributos (DATA/DOCUMENT, obrigatório/opcional), rótulos `label_pt`/`label_en`.
- **Estados da empresa**: `PENDING_DOCUMENTS → UNDER_REVIEW → APPROVED/REJECTED/CHANGES_REQUESTED`; `APPROVED → PENDING_UPDATE` quando o admin publica requisito novo; resubmit volta a `UNDER_REVIEW`. `nextStep` calculado no servidor.
- **Pré-aprovação**: a empresa só vê status.

## Ambiente

- Gate por slice: `./mvnw clean verify` — testes rodam **offline** (profile `%test` sem DB/Docker). Tudo deve passar.
- `./mvnw quarkus:dev` usa `.env` na raiz (já existe): credenciais S3 do bucket `ecolchain-bucket-docs-dev` **e credenciais SMTP reais** — o OCI Email Delivery está **ativo e funciona** (sender `ecolchain@ecolchain.com`, domínio e DKIM ACTIVE, SMTP `smtp.email.sa-saopaulo-1.oci.oraclecloud.com:465`). Em dev o OTP chega por e-mail de verdade; cota 200 e-mails/24 h e 10/min — testes sempre com `MockMailbox`; crie profile de mock por config para loops repetitivos.
- O ADB Always Free auto-pausa após ~7 dias: se conexão falhar, assuma banco parado (precisa console OCI) — registre em `docs/DUVIDAS.md` e mantenha os testes offline verdes.
- **Prod é read-only**: nunca profile `%prod` fora de deploy, nunca escrever no bucket prod.
- OCI CLI para **validação** de recursos da tenancy ecolchain: `~/.oci/config` aponta para outra tenancy; crie `/tmp/oci-ecolchain-config` (instruções no handoff, seção 1) e use `OCI_CLI_CONFIG_FILE`. Somente leitura — infra é Terraform.
- Mermaid alterado? Valide com `/tmp/ecol-diagrams/render.sh <arquivo.md>`.

## Regras invioláveis

- **API-first**: escreva `openapi/management-api.yaml` **antes** de qualquer controller; gere interfaces via `openapi-generator-maven-plugin` (`jaxrs-spec`, `interfaceOnly`, `apiPackage=com.ecolchain.api.contract.api`, `modelPackage=com.ecolchain.api.contract.model`); controllers só implementam as interfaces geradas. Nunca edite código gerado; nunca crie endpoint fora do contrato.
- **Hexagonal por contexto**: `com.ecolchain.api.<contexto>` (`identity`, `onboarding`, `catalog`, `documents`, `common`) com `domain` (puro, sem Quarkus) / `application` (`port.in` = use cases, `port.out` = stores/integrations, `usecase` = implementações) / `adapter` (`in.web` controllers, `out.persistence` Panache, `out.storage` S3, `out.mail` SMTP, `out.security` JWT). Controller só traduz HTTP↔use case.
- **Envelope obrigatório** em TODA resposta: `{ "data": {}, "links": [], "erros": [] }`. `erros[]` = objetos Problem Details **RFC 9457** (`type`, `title`, `status`, `detail`, `instance`) + `code` estável (ex.: `OTP_INVALID`, `CNPJ_IN_USE`) + `campo` opcional. Montagem central por `ExceptionMapper`/`@ServerExceptionMapper` — controllers retornam só o `data`. Status HTTP semântico.
- **Contrato**: campos request/response em **pt-BR camelCase** (`razaoSocial`, `tiposPerfil`, `obrigatorio`); paths em inglês; schemas nomeados `<Nome>Request`/`<Nome>Response` — **nunca** `*Dto`.
- **Doc por use case**: `docs/features/<use-case>.md` descreve o fluxo; a classe referencia o arquivo no Javadoc. Código mudou → doc muda junto.
- **Clean Code + SOLID**, inglês no código, zero gambiarras.
- **Segurança**: OTP como HMAC-SHA256 (+pepper), TTL 10 min, 5 tentativas, uso único, resposta genérica anti-enumeração, cooldown 60 s e teto 5/h por e-mail + teto por IP; refresh opaco 256 bits com SHA-256 no banco, rotativo, reuso revoga a família; `/admin/**` confere papel **no banco** além do claim JWT; deny by default; nunca logar OTP/tokens/e-mail completo.
- **Segredos**: só `keys/*.pem` é permitido no repo (decisão do dono — exceção no `.gitignore`, allow-list no `ConfigGuardTest`); nenhum outro segredo commitado.

## Plano (detalhe em `docs/handoff-auth-onboarding.md`, seção 4)

0. **Docs/limpeza**: commit inicial na branch `feat/auth-onboarding` com as docs já alteradas + deleções staged dos arquivos-template do Quarkus.
1. **Base**: `openapi/management-api.yaml` com todos os paths do Anexo B (envelope `ApiResponse`, `Problem`, `Link` via `allOf`); plugin do gerador no `pom.xml`; `common` (envelope + mappers + `BusinessException`); esqueleto hexagonal; `docs/features/README.md`.
2. **Auth**: `V2__identity.sql` (`ACCOUNT`, `EMAIL_OTP`, `REFRESH_TOKEN` + seed admin); `scripts/gen-jwt-keys.sh` + `keys/*.pem` commitados + properties `%dev`/`%prod` do C.1; use cases `RequestOtp`/`VerifyOtp`/`RefreshSession`/`Logout`/`GetMe`; **template HTML do e-mail de OTP** (pt-BR/en, código em destaque, fallback texto — Qute/`src/main/resources/templates/`); controllers `/auth/*` + `/me`; testes completos (MockMailbox, TTL/tentativas/uso único, JWT, rotação/reuso de refresh, `/me` nextStep, role check).
3. **Catálogo e cadastro**: `V3__catalog.sql` + seed completo do Anexo D (6 tipos, passos, atributos bilíngues, `rules`); `V4__company.sql` (`COMPANY`, `COMPANY_PROFILE`, `COMPANY_MEMBER`, `COMPANY_ATTRIBUTE_VALUE`); validador de CNPJ alfanumérico (oficial `12.ABC.345/01DE-35`); admin CRUD de tipos/atributos + `PUT /admin/profile-types/{code}/flow` atômico (upsert por `code`) + `AUDIT_LOG`; `PUT /me/company` multi-perfil (livre até submeter); `GET /public/profile-types`; `GET /me/onboarding` (união dos fluxos, dedup por `code`, valores, progresso).
4. **Documentos e revisão**: `V5` (`DOCUMENT`, `AUDIT_LOG` se ainda não); `DocumentStorageService` vira `adapter.out.storage` implementando port `ObjectStorage`; `upload-url`/`confirm`/`download-url`, `PUT attributes/{code}`, `submit`; admin fila/detalhe/download/review; gatilho `PENDING_UPDATE`; remover `DevDocumentResource` (+ seu teste de profile-gate) — substituído; testes de ownership/HEAD/submit/review/pending_update.
5. **Fechamento**: coleção Bruno cobrindo o fluxo todo; `README.md` (como rodar); `docs/RELATO-IMPLEMENTACAO.md` (feito/pendente/desvios/como testar).

## Protocolo de dúvidas

Usuário indisponível — **não pergunte**. Dúvida → item `- [ ]` em `docs/DUVIDAS.md` com contexto, opções e a decisão adotada; adote a mais defensável e siga. Travou em dependência externa (ex.: ADB parada)? Registre, pule o dependente e continue nas partes independentes. Não desvie de D1–D13 sem documentar no `DUVIDAS.md` e no `RELATO`.

## Guardrails

- Branch `feat/auth-onboarding`, um commit por slice (`feat:`/`chore:` + descrição). **Sem push, sem PR.**
- Não tocar no repo de infra (`../ecolchain-infra-general`) nem no `ecolchain-management-web`.
- Fora de escopo: operadores/funcionários, PF, senha/2FA, painel admin web, integração real gov.br/SINIR/SIGOR, antivírus de upload, docs por carga (MTR/CNH/MOPP — operação, não onboarding).

Ao terminar: `./mvnw clean verify` verde, `docs/RELATO-IMPLEMENTACAO.md` escrito, `docs/DUVIDAS.md` com os itens abertos listados.

---
