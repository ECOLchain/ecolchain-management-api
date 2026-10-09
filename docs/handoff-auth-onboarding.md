# Handoff — implementação do MVP de auth + onboarding

Este arquivo dá a uma **sessão nova** todo o contexto e o plano para implementar o MVP completo sem precisar perguntar nada ao usuário (ele estará indisponível). As fontes da verdade são:

1. `AGENTS.md` — comandos, integração OCI e **regras de código invioláveis** (hexagonal, API-first, envelope, idiomas, naming, doc por use case).
2. `docs/proposta-auth-onboarding.md` — decisões D1–D13, C4, fluxos (5.x), modelo de dados (Anexo A), endpoints (Anexo B), segurança/chaves/e-mail (Anexo C), **matriz de atributos por perfil — seed** (Anexo D).

Leia os dois por inteiro antes de escrever qualquer linha.

## 1. Ambiente e acessos disponíveis

- Repo: `/Users/leandro/Dev/ecolchain/ecolchain-management-api` (Quarkus 3.40.1, Java 25, Maven wrapper).
- **Testes**: `./mvnw clean verify` — rodam 100% offline (profile `%test` sem DB/Cloud/Docker). É o gate de cada slice.
- **Dev local**: `./mvnw quarkus:dev` — precisa de `.env` (já existe na raiz, com credenciais S3 do bucket dev **e credenciais SMTP**). OCI Email Delivery **está ativo e funciona** (sender/domínio/DKIM ACTIVE — validado via `oci` CLI): em dev o OTP chega de verdade por e-mail. Cuidado com a cota de 200 e-mails/24 h — testes usam `MockMailbox`; para loops repetitivos de dev há opção de mock por config. **O que falta: o template HTML do e-mail** (faz parte da slice 1).
- **OCI CLI da tenancy ecolchain**: o `~/.oci/config` aponta para outra tenancy (`oci os` não vê os buckets). Para validar recursos OCI da ecolchain, crie `/tmp/oci-ecolchain-config` com `[DEFAULT]`, `user`/`tenancy`/`fingerprint` de `infra/terraform/oci/email-delivery/env/terraform.tfvars`, `key_file=/Users/leandro/.oci/ecolchain_terraform.pem`, `region=sa-saopaulo-1`; rode com `OCI_CLI_CONFIG_FILE=/tmp/oci-ecolchain-config OCI_CLI_SUPPRESS_FILE_PERMISSIONS_WARNING=True`. Só leitura/validação — não crie nem altere recursos por CLI (a infra é Terraform).
- **ADB**: `MGMT_DEV` via URL em `application.properties`. O ADB Always Free **auto-pausa após ~7 dias**; se a conexão falhar no startup/teste de integração, o banco pode estar parado — isso exige console OCI (o usuário resolve depois). Nesses casos: mantenha os testes unitários/offline verdes e registre em `docs/DUVIDAS.md`.
- **Bucket dev**: `ecolchain-bucket-docs-dev`, presign S3-compat já validado E2E (`DocumentStorageService`). Teste de integração de presign usa as credenciais do `.env` — rode se puder, mas nunca escreva no bucket `prod`.
- **Prod é read-only**: nunca subir a app com profile prod, nunca escrever no bucket prod.
- Mermaid (se criar/alterar diagramas): `/tmp/ecol-diagrams/render.sh <arquivo.md>` valida todos os blocos (mermaid-cli + Chrome do sistema já instalados).
- **Sem push**: trabalhe na branch `feat/auth-onboarding`, um commit por slice. Não abra PR nem faça push.

## 2. Estado atual do working tree

- `docs/proposta-auth-onboarding.md`, `AGENTS.md`, `README.md` modificados (proposta final + regras) — **commitar primeiro** como commit de docs na branch.
- Deleções staged de `GreetingResource.java` e `MyLivenessCheck.java` (sobras do template Quarkus) — incluir nesse commit de arrumação.
- `DevDocumentResource` é temporário e **será removido** na slice 3 (substituído pelos endpoints autenticados).

## 3. Regras invioláveis (resumo — AGENTS.md manda)

- **API-first**: `openapi/management-api.yaml` primeiro; interfaces JAX-RS geradas pelo `openapi-generator-maven-plugin` (generator `jaxrs-spec`, `interfaceOnly`, `modelPackage=com.ecolchain.api.contract.model`); controllers só implementam. Nunca editar gerado.
- **Hexagonal**: `domain` puro / `application` (`port.in`, `port.out`, `usecase`) / `adapter` (`in.web`, `out.persistence`, `out.storage`, `out.mail`, `out.security`). Controller só traduz HTTP↔use case.
- **Envelope obrigatório**: toda resposta `{data, links, erros}`; `erros[]` = Problem Details RFC 9457 (`type`, `title`, `status`, `detail`, `instance`) + `code` estável + `campo` opcional. Montagem central via `ExceptionMapper`/`@ServerExceptionMapper` (um grupo de mappers, exceções de domínio com `code`).
- **Contrato**: campos pt-BR camelCase (`razaoSocial`, `nomeCompleto`, `tiposPerfil`); paths em inglês; schemas `<Nome>Request`/`<Nome>Response`; nunca `*Dto`.
- **Doc por use case**: `docs/features/<feature>.md` + link no Javadoc da classe. PR sem doc atualizado = incompleto.
- **Clean Code + SOLID**, sem gambiarras.
- **Segredos**: nada de segredo novo no repo além de `keys/*.pem` (exceção aprovada). OTP/token/e-mail completo nunca em log.

## 4. Plano de execução (slices → commits)

### Slice 0 — Contrato e base (commit: `feat: OpenAPI contract + envelope + hexagonal skeleton`)

- `pom.xml`: adicionar `quarkus-smallrye-jwt`, `quarkus-smallrye-jwt-build`, `quarkus-mailer`, `quarkus-hibernate-validator` (se ausente) e o plugin `openapi-generator-maven-plugin` (versão estável ≥7.x, não `latest`). Configurar `jaxrs-spec`: `inputSpec=openapi/management-api.yaml`, `interfaceOnly=true`, `apiPackage=com.ecolchain.api.contract.api`, `modelPackage=com.ecolchain.api.contract.model`, `generateSupportingFiles=false` onde fizer sentido, `skip`/`generateAliasAsModel` conforme necessário; bind no `generate-sources`.
- `openapi/management-api.yaml`: cobrir **todos** os paths do Anexo B da proposta (públicos, `/me`, `/admin`). Components: `Envelope` (data/links/erros) usado via `allOf` por resposta, `Problem` (RFC 9457 + `code`/`campo`), `Link` (`rel`, `href`, `metodo`), security `bearerAuth` (JWT) + `cookieAuth` (refresh). Campos pt-BR camelCase.
- `common`: `ApiResponse`, `Link`, `Problem`, `ProblemFactory`, `BusinessException` (com `code`), grupo de mappers (`@ServerExceptionMapper`/`ExceptionMapper` para `BusinessException`, `ConstraintViolationException`, `WebApplicationException`, `Throwable` fallback) e resolução de idioma dos rótulos (`Accept-Language`, default pt-BR).
- Esqueleto de pacotes `identity`, `onboarding`, `catalog`, `documents` conforme 3.2 da proposta.
- `docs/features/README.md` explicando a convenção doc↔código.

### Slice 1 — Auth (commit: `feat: email OTP + JWT + refresh + /me`)

- Migration `V2__identity.sql`: `ACCOUNT`, `EMAIL_OTP`, `REFRESH_TOKEN` + seed do admin (`leandroluz201616@gmail.com`, `PLATFORM_ADMIN`, `EMAIL_VERIFIED`). Colunas conforme Anexo A.
- `keys/`: gerar pares RSA 2048 dev/prod (script `scripts/gen-jwt-keys.sh`, idempotente), commitar os 4 PEMs, exceção no `.gitignore` (`!keys/*.pem`), properties de C.1 (`%dev`/`%prod`), `ConfigGuardTest` com allow-list de `keys/` continuando a bloquear outros segredos.
- Domínio: `OtpPolicy` (6 dígitos, HMAC-SHA256 com pepper, TTL 10 min, 5 tentativas, uso único, cooldown 60 s, máx 5/h por e-mail, resposta genérica), rotação de refresh (256 bits, SHA-256, família 30 dias abs., reuso revoga família).
- Ports/use cases/adapters: `RequestOtp`, `VerifyOtp`, `RefreshSession`, `Logout`, `GetMe`; adapters `out.mail` (Quarkus Mailer, envio **pós-commit**), `out.security` (SmallRye JWT, RS256, `kid`, claims `iss`/`aud`/`sub`/`groups`/`jti`), `out.persistence` Panache.
- Controllers `/auth/*` e `GET /me` implementando as interfaces geradas; cookie `HttpOnly; Secure; SameSite=Strict; Path=/auth`; deny by default (`/auth/**`, `/public/**`, health, JWKS abertos); `/admin/**` confere papel **no banco**.
- **Template do e-mail de OTP**: HTML transacional simples e "bonito" (logo/wordmark Ecolchain se houver asset no repo, senão tipografia limpa), código em destaque (`font-size` grande, `letter-spacing`, caixa com fundo), validade de 10 min, aviso "se não foi você, ignore" e fallback texto. pt-BR e en (escolha por `Accept-Language`). Template em arquivo (`src/main/resources/templates/` com Qute, ou o mecanismo de template do Quarkus Mailer) — nunca HTML concatenado no use case.
- `docs/features/request-otp.md`, `verify-otp.md`, `refresh-session.md`, `logout.md`, `get-me.md`.
- Testes: `@QuarkusTest` + `MockMailbox` (código chega ao "e-mail"), TTL, tentativas, uso único, resposta idêntica conta nova/existente, JWT claims/expiração, rotação e reuso de refresh, `/me` com `nextStep`, `COMPANY_OWNER` barrado em `/admin`.

### Slice 2 — Catálogo e cadastro (commit: `feat: dynamic catalog + company registration`)

- Migration `V3__catalog.sql`: `PROFILE_TYPE`, `ONBOARDING_STEP`, `ATTRIBUTE_DEFINITION`, `STEP_ATTRIBUTE` + **seed completo do Anexo D** (6 tipos, passos, atributos com `label_pt`/`label_en`, `help_pt`/`help_en`, `rules`).
- Migration `V4__company.sql`: `COMPANY`, `COMPANY_PROFILE` (N tipos), `COMPANY_MEMBER`, `COMPANY_ATTRIBUTE_VALUE`.
- Validador de CNPJ alfanumérico (módulo 11, `ASCII − 48`, exemplo oficial `12.ABC.345/01DE-35`), `VARCHAR2(14)` maiúsculo, único entre não rejeitadas; `CnpjLookup` port + adapter HTTP opcional (nunca bloqueia cadastro).
- Use cases admin: CRUD `profile-types` (desativar, não apagar), `PUT .../flow` (replace atômico, upsert de atributos por `code`), CRUD `attributes`; `AUDIT_LOG` nas mudanças.
- Use cases empresa: `PUT /me/company` (cria/atualiza até submeter; valida tipos ativos, declaração e termos versionados), `GET /public/profile-types`, `GET /me/onboarding` (schema = **união** dos fluxos, dedup por `code`, valores + progresso + status).
- Estados: `PENDING_DOCUMENTS` ao criar; máquina de 5.3; `nextStep` calculado no servidor.
- `docs/features/` para cada use case. Testes: CNPJ (oficial + inválidos + unicidade), replace de fluxo atômico, união/dedup multi-perfil, troca de tipo antes do submit, schema servido com rótulos por idioma.

### Slice 3 — Documentos e revisão (commit: `feat: documents presign/confirm + review`)

- Migration `V5__documents.sql`: `DOCUMENT` (PENDING/CONFIRMED, `object_key` UK) — `AUDIT_LOG` pode entrar aqui ou no V4.
- `adapter.out.storage`: evoluir `DocumentStorageService` para implementar a port `ObjectStorage` (presign PUT/GET, HEAD); chave `docs/<companyId>/<attributeCode>/<uuid>-<filename>`; validação de extensão/tipo/tamanho pelas `rules` do atributo ∩ política global.
- Endpoints `/me/onboarding/documents/{code}/upload-url|confirm|download-url`, `PUT /me/onboarding/attributes/{code}` (DATA), `POST /me/onboarding/submit` (completude dos obrigatórios da união).
- Admin: fila `/admin/companies`, detalhe, `download-url` para revisar, `POST .../review` (APPROVE/REQUEST_CHANGES/REJECT por item + geral) + `AUDIT_LOG`.
- **Gatilho `PENDING_UPDATE`**: ao publicar fluxo com requisito obrigatório novo, empresas `APPROVED` daquele tipo viram `PENDING_UPDATE`; resubmit → `UNDER_REVIEW`.
- Remover `DevDocumentResource` e seu teste de profile-gate (substituído).
- `docs/features/` + testes: ownership (empresa não confirma chave alheia), HEAD falhando, submit incompleto, transições de review, `PENDING_UPDATE`.

### Slice 4 — Fechamento (commit: `chore: bruno collection + report`)

- Coleção Bruno (`collections/` ou `bruno/`) cobrindo o fluxo inteiro: request → verify → company → onboarding → upload → submit → review admin.
- `README.md`: como rodar (local, `.env`, keys, Bruno), links para proposta e `docs/features/`.
- `docs/RELATO-IMPLEMENTACAO.md`: o que foi feito, decisões tomadas, desvios do plano, como testar E2E, e lista final do `DUVIDAS.md`.

## 5. Protocolo de dúvidas (o usuário está indisponível)

- **Nunca pergunte ao usuário.** Dúvida → escreva em `docs/DUVIDAS.md` no formato:
  `- [ ] **<tema>** — contexto, opções consideradas, decisão adotada e por quê.`
- Adote a decisão mais defensável, deixe o marcador `- [ ]` aberto e siga em frente. Se algo travar de verdade (ex.: ADB parado), pule a parte dependente, registre e continue nas independentes.
- Não mude decisões D1–D13 silenciosamente; se precisar desviar, documente no `DUVIDAS.md` e no `RELATO`.

## 6. Não fazer

- Não faça push, não abra PR, não toque no repo de infra nem em `ecolchain-management-web`.
- Não implemente operadores/funcionários, PF, senha, 2FA, painel admin web, gov.br real, integração SINIR/SIGOR real, anti-vírus de upload.
- Não exponha segredos em logs/respostas; não versione nada sensível fora de `keys/`.

## 7. Prompt para a nova sessão

O prompt completo e autocontido está em **`docs/PROMPT-NOVA-SESSAO.md`** — cole o conteúdo entre as linhas `---` na sessão nova.
