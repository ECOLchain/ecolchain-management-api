# Proposta: sign-up / sign-in com JWT e onboarding configurável

> **Status:** rascunho para discussão · **Escopo:** MVP · **Repo:** `ecolchain-management-api`
>
> Para a conversa, o essencial está nas seções 3 (decisões D1 a D13), 4 (C4) e 6 (perguntas abertas). Os anexos A, B e C têm o detalhe: modelo de dados, endpoints, segurança.
>
> **Lacuna:** a lista de dados e documentos por tipo de perfil não veio na mensagem (só os 6 nomes). O catálogo nasce com os 6 tipos e **sem atributos**; os exemplos abaixo são ilustrativos.

Os diagramas são Mermaid: o GitHub renderiza direto e o preview de Markdown do IntelliJ também (pode pedir para baixar a extensão na primeira vez).

## 1. Resumo e escopo

1. **E-mail + código (OTP), sem senha.** O mesmo fluxo faz sign-up e sign-in.
2. **JWT** emitido pela própria API (RS256): access token curto + refresh token rotativo. Chaves geradas localmente no MVP.
3. **Cadastro em 2 passos:** (1) e-mail + código; (2) CNPJ, razão social, nome completo e **tipo de perfil**.
4. **Onboarding dinâmico:** depois do cadastro o app pede dados e documentos conforme o tipo de perfil. Tipos, atributos e passos são **dados no banco, editados por endpoints de admin**: mudar o fluxo não exige deploy.
5. **Documentos** vão do navegador direto para o OCI Object Storage (URL pré-assinada). O banco guarda só a **chave (string)**.
6. **Revisão pelo admin** (aprova, pede ajustes ou rejeita) antes de a empresa ficar `APPROVED`.

**Dentro do MVP:** PJ (CNPJ); 1 login por empresa, do dono ou representante legal.
**Fora, por enquanto:** operadores e funcionários, PF, senha e 2FA, KYC automático, vínculo com carteira on-chain, painel web do admin (no MVP o admin usa Bruno ou curl) e troca de e-mail ou de titular do login (no MVP, só o admin faz, como suporte).

| Pedido | Onde está |
|---|---|
| Sign-in e sign-up com JWT no Quarkus, simples e seguro | D1 a D4, 5.1 |
| Chaves geradas localmente (MVP) | D3, C.1 |
| Cadastro por e-mail + código via OCI Email Delivery | D1, D11, 5.1, C.2 |
| CNPJ, nome completo, razão social, tipo de perfil | D10, 5.1 |
| Tipos de perfil servidos por endpoint do admin (6 iniciais) | D6, A, B |
| Só PJ; dono ou representante legal | D10, 5.1 |
| Documentos por tipo de perfil, valores como string, presign no bucket OCI | D6 a D8, 5.2, A |
| Admin muda fluxos sem deploy | D6, 3.1, A |
| Empresa só com o login dela (sem operadores) | D9 |
| Diagrama C4 | 4 |

## 2. O que já existe

| Peça | Hoje | Consequência |
|---|---|---|
| API | Quarkus 3.40 (Java 25), Panache, Flyway, Oracle ADB 23ai (`MGMT_DEV` e `MGMT_PROD`), OpenAPI, logs JSON. Pacote único `com.ecolchain.api` | Entram `quarkus-smallrye-jwt`, `quarkus-smallrye-jwt-build` e `quarkus-mailer`. Direção já definida no `AGENTS.md`: **API-first** (contrato OpenAPI primeiro, interfaces geradas pelo OpenAPI Generator, implementação depois) |
| Documentos | `DocumentStorageService` gera URLs pré-assinadas (PUT 10 min, GET 5 min, máx. 25 MiB, extensões em allow-list). `DevDocumentResource` só existe no profile `dev`, **não tem auth** e é temporário (será substituído). A chave (`docs/<uuid>-<arquivo>`) **não tem dono** | Reaproveitar o serviço. O upload-url vira `POST` e cria uma intenção de upload (`DOCUMENT`), com prefixo por empresa e checagem de dono (D8) |
| E-mail | Módulo Terraform `oci/email-delivery` em andamento (branch `feat/oci-email-delivery` do infra): Email Domain `ecolchain.com`, DKIM no Cloudflare, Approved Sender `ecolchain@ecolchain.com`, credencial SMTP. O README da API já documenta o uso (SMTP 465 com TLS implícito, mock em dev e test). **Limite da tenancy: 200 e-mails/24 h e 10/min** | Base do envio do OTP (D11). O limite exige rate limit desde o primeiro dia |
| Front | `ecolchain-core-web` (React; `app.ecolchain.com`, dev em `app-dev-tester.ecolchain.com`) hoje só conecta carteira Solana: não há login | Passa a usar `/auth` e `/me`. O login da empresa é separado da carteira |
| Hospedagem da API | Ainda não existe no infra | Afeta cookie, CORS e entrega das chaves (seção 6, pergunta 9) |
| On-chain | `Participante.kyc_hash` é informado pelo operador no cadastro on-chain. Os papéis on-chain (Coletor, Cooperativa, Transportador, Indústria) diferem dos 6 tipos desta proposta | Futuro: esta API vira a fonte do cadastro/KYC off-chain e do `kyc_hash`. Fora do escopo agora |

## 3. Decisões propostas

| # | Tema | Proposta | Por quê / alternativa |
|---|---|---|---|
| D1 | Credencial | E-mail + código numérico (OTP) de 6 dígitos, **sem senha**. O mesmo `request`/`verify` serve para sign-up e sign-in | Não há senha para guardar, vazar ou resetar, e a resposta é igual exista ou não a conta (sem enumeração). Alternativa: senha + OTP só no cadastro (mais atrito e mais superfície) |
| D2 | Sessão | **Access JWT** de 15 min (RS256) + **refresh token opaco** de 7 dias, rotativo, com detecção de reuso. Refresh em cookie `HttpOnly; Secure; SameSite=Strict; Path=/auth`; access em memória no SPA | Token curto limita o estrago e o refresh permite logout e revogação de verdade. Exige a API sob `*.ecolchain.com` (mesmo site do SPA); se não der, o refresh vai no corpo da resposta. Alternativa mais simples: um único JWT de 1 a 8 h, sem revogação |
| D3 | Chaves JWT (MVP) | Par RSA 2048 gerado por script local; **a privada fica fora do git**; `kid` no header; no deploy a chave entra por arquivo montado ou variável de ambiente | Detalhes em C.1. Evolução: OCI Vault e rotação com JWKS |
| D4 | Autorização | Claim `groups` = `COMPANY_OWNER` ou `PLATFORM_ADMIN`; `@RolesAllowed` por prefixo (`/me/**`, `/admin/**`); deny by default nas demais rotas. Estado da conta e da empresa é lido do banco, não do token. Em `/admin/**` o papel também é conferido no banco | O token só prova identidade. Regra de negócio fica sempre no servidor, e a conferência no banco reduz o estrago se a chave vazar |
| D5 | Admin | Allow-list de e-mails em config (`app.auth.admin-emails`): quem estiver nela vira `PLATFORM_ADMIN` no primeiro login | Bootstrap mínimo. Depois: convite de admins por endpoint e 2º fator |
| D6 | Catálogo dinâmico | `PROFILE_TYPE` → `ONBOARDING_STEP` → `STEP_ATTRIBUTE` → `ATTRIBUTE_DEFINITION` no banco, editados por endpoint. A API devolve o **schema do formulário** e o SPA só renderiza | Exigir outro documento ou dado vira 1 chamada, sem deploy da API nem do SPA |
| D7 | Valores | Tudo `VARCHAR2`: dado = texto; documento = **chave do objeto** no bucket | Como pedido. A validação (regex, tamanho, extensão) vem das `rules` do atributo e só pode **restringir** a política global (`app.docs.*`) |
| D8 | Documentos | `POST upload-url` cria uma intenção de upload (`DOCUMENT` PENDING) e devolve o presign PUT → upload direto → `confirm` (a API faz HEAD no objeto, marca `CONFIRMED` e grava a chave no atributo). Chave `docs/<companyId>/<atributo>/<uuid>-<arquivo>`; a API só confirma chaves que ela mesma emitiu para aquela empresa | Impede que uma empresa "reivindique" arquivo de outra (IDOR) e deixa rastro de uploads órfãos para limpeza. Reaproveita TTL e limite atuais |
| D9 | Tenant | `ACCOUNT` ↔ `COMPANY_MEMBER` (role `OWNER`) ↔ `COMPANY`, com 1 membro por empresa | Operadores e funcionários entram depois com novas linhas e convite, sem migrar dados |
| D10 | Empresa (PJ) | Núcleo fixo: CNPJ, razão social, nome completo, tipo de perfil. CNPJ validado por dígito verificador (**já aceita o formato alfanumérico**, em vigor desde jul/2026) e único entre as empresas não rejeitadas; consulta pública opcional só para pré-preencher a razão social. Declaração "sou dono ou representante legal" + aceite de termos versionado | **Nome completo** (a pessoa) e **razão social** (a empresa) não são a mesma coisa: coincidem só em EI e MEI (no MEI a razão social vem com o CPF). Pedimos os dois |
| D11 | E-mail | OCI Email Delivery por SMTP via Quarkus Mailer, como já definido no módulo de infra e no README. Mock em dev e test | Detalhes em C.2 |
| D12 | Aprovação | Revisão manual do admin: aprova, pede ajustes (com motivo) ou rejeita, por documento e no geral | Mínimo razoável para cadastro regulado. Evolui para regras automáticas |
| D13 | Erros | Formato RFC 9457 (`application/problem+json`) com `code` estável (ex.: `OTP_INVALID`, `CNPJ_IN_USE`), emitido por um `ExceptionMapper` único. Na auth, código errado, expirado ou inexistente devolve sempre o mesmo `code` | O SPA traduz pelo `code`, sem depender de texto. Hoje o `DevDocumentResource` devolve `{"error": "..."}` ad hoc |

**Alternativa descartada no MVP:** IdP gerenciado (OCI IAM Identity Domains, Keycloak, Auth0). Entrega MFA, reset e login social prontos, mas é mais uma peça para operar e pagar, e o onboarding dinâmico continuaria sendo da API. Se um dia entrar, tende a ser troca de configuração de verificação do token, não de regra de negócio.

### 3.1 O que é dinâmico e o que é estático

| Dinâmico (admin, por endpoint, sem deploy) | Estático (código ou config, muda com deploy ou restart) |
|---|---|
| Tipos de perfil: nome, descrição, ativo, ordem | Núcleo da empresa: e-mail, CNPJ, razão social, nome completo, tipo |
| Atributos: rótulo, ajuda, `DATA` ou `DOCUMENT`, regras (regex, tamanho, extensões) | OTP e JWT: algoritmo, TTLs, limites de tentativa |
| Passos do fluxo, ordem, obrigatório ou opcional | Roles e matriz de autorização |
| Decisões de revisão por documento e por empresa | Máquina de estados da empresa e política global de upload (`app.docs.*`) |

Os parâmetros de segurança ficam estáticos de propósito: menos superfície de ataque se um admin for comprometido.

## 4. Arquitetura (C4)

Notação C4 (pessoa, sistema, container, componente) desenhada com `flowchart` do Mermaid, porque o `C4Context` nativo do Mermaid sobrepõe rótulos e cruza setas quando passa de umas 5 relações.

### 4.1 Contexto (nível 1)

```mermaid
%%{init: {"flowchart": {"wrappingWidth": 360}}}%%
flowchart TB
    classDef person fill:#08427b,stroke:#052e56,color:#ffffff
    classDef system fill:#1168bd,stroke:#0b4884,color:#ffffff
    classDef ext fill:#999999,stroke:#6b6b6b,color:#ffffff

    owner("<b>Representante legal</b><br/><i>[Pessoa]</i><br/>Dono ou representante<br/>legal da PJ"):::person
    admin("<b>Admin Ecolchain</b><br/><i>[Pessoa]</i><br/>Dono da plataforma"):::person

    web["<b>Ecolchain Core Web</b><br/><i>[Sistema]</i><br/>SPA React: cadastro<br/>e onboarding"]:::system
    api["<b>Ecolchain<br/>Management API</b><br/><i>[Sistema em foco]</i><br/>Identidade, JWT, cadastro<br/>e onboarding configurável"]:::system

    mail["<b>OCI Email Delivery</b><br/><i>[Sistema externo]</i><br/>Envia o código<br/>de verificação"]:::ext
    cnpj["<b>Consulta de CNPJ</b><br/><i>[Sistema externo]</i><br/>API pública, opcional"]:::ext

    owner -->|"Cadastro, login<br/>e documentos<br/><i>[HTTPS]</i>"| web
    web -->|"Chama<br/><i>[JSON, HTTPS, JWT]</i>"| api
    admin -->|"Configura fluxos e<br/>revisa cadastros<br/><i>[HTTPS, JWT]</i><br/><i>Bruno ou curl no MVP</i>"| api
    api -->|"Envia código<br/><i>[SMTP]</i>"| mail
    api -->|"Consulta razão social<br/><i>[HTTPS]</i>"| cnpj
```

Legenda: azul-escuro = pessoa; azul = sistema; azul médio = container; azul claro = componente; cinza = sistema externo; tracejado = fronteira do que está em foco. Entre colchetes, o tipo e a tecnologia.

### 4.2 Containers (nível 2)

```mermaid
%%{init: {"flowchart": {"wrappingWidth": 360}}}%%
flowchart TB
    classDef person fill:#08427b,stroke:#052e56,color:#ffffff
    classDef system fill:#1168bd,stroke:#0b4884,color:#ffffff
    classDef ext fill:#999999,stroke:#6b6b6b,color:#ffffff
    classDef container fill:#438dd5,stroke:#2e6295,color:#ffffff

    owner("<b>Representante legal</b><br/><i>[Pessoa]</i>"):::person
    admin("<b>Admin Ecolchain</b><br/><i>[Pessoa]</i>"):::person
    web["<b>Ecolchain Core Web</b><br/><i>[Sistema]</i><br/>SPA React"]:::system
    mail["<b>OCI Email Delivery</b><br/><i>[Sistema externo]</i><br/>SMTP"]:::ext
    cnpj["<b>Consulta de CNPJ</b><br/><i>[Sistema externo]</i><br/>Opcional"]:::ext

    subgraph mgmt["Ecolchain Management  <i>[Sistema em foco]</i>"]
        api["<b>Management API</b><br/><i>[Container: Quarkus 3.40,<br/>Java 25]</i><br/>Auth OTP e JWT, onboarding,<br/>catálogo, revisão, presign"]:::container
        db[("<b>Autonomous Database</b><br/><i>[Container: Oracle 23ai]</i><br/>Contas, OTP, refresh,<br/>catálogo, empresas,<br/>valores, auditoria")]:::container
        bucket[("<b>Bucket de documentos</b><br/><i>[Container: OCI<br/>Object Storage]</i><br/>Privado, um por ambiente")]:::container
    end
    style mgmt fill:#ffffff,stroke:#444444,stroke-dasharray:6 4,color:#222222

    owner -->|"Usa<br/><i>[HTTPS]</i>"| web
    web -->|"Auth e onboarding<br/><i>[JSON, HTTPS, JWT]</i>"| api
    admin -->|"Catálogo e revisão<br/><i>[HTTPS, JWT]</i>"| api
    api -->|"Lê e grava<br/><i>[JDBC, TLS]</i>"| db
    api -->|"Presign e HEAD<br/><i>[S3-compat]</i>"| bucket
    web -->|"Upload e download direto<br/><i>[URL pré-assinada]</i>"| bucket
    api -->|"Código OTP<br/><i>[SMTP]</i>"| mail
    api -->|"Consulta CNPJ<br/><i>[HTTPS]</i>"| cnpj
```

### 4.3 Componentes: autenticação (nível 3)

```mermaid
%%{init: {"flowchart": {"wrappingWidth": 260}}}%%
flowchart TB
    classDef container fill:#438dd5,stroke:#2e6295,color:#ffffff
    classDef ext fill:#999999,stroke:#6b6b6b,color:#ffffff
    classDef component fill:#85bbf0,stroke:#5d82a8,color:#000000

    web["<b>Core Web</b><br/><i>[Container: React SPA]</i><br/>Cliente HTTP"]:::container
    mail["<b>OCI Email Delivery</b><br/><i>[Sistema externo]</i><br/>SMTP"]:::ext
    db[("<b>Autonomous Database</b><br/><i>[Container: Oracle 23ai]</i><br/>ACCOUNT, EMAIL_OTP,<br/>REFRESH_TOKEN")]:::container

    subgraph api["Management API  <i>[Container: Quarkus]</i>"]
        authRes["<b>AuthResource</b><br/><i>[Component: JAX-RS /auth]</i><br/>request, verify,<br/>refresh, logout"]:::component
        otp["<b>OtpService</b><br/><i>[Component: CDI]</i><br/>Gera e valida o código:<br/>HMAC, TTL, tentativas,<br/>cooldown"]:::component
        accounts["<b>AccountService</b><br/><i>[Component: CDI]</i><br/>Cria a conta no 1º login<br/>e aplica a allow-list<br/>de admins"]:::component
        tokens["<b>TokenService</b><br/><i>[Component: SmallRye<br/>JWT Build]</i><br/>Assina o access JWT<br/>(RS256, kid). Emite e<br/>rotaciona o refresh"]:::component
        mailer["<b>EmailSender</b><br/><i>[Component: Quarkus Mailer]</i><br/>Envia o código após<br/>o commit. Mock em<br/>dev e test"]:::component
    end
    style api fill:#ffffff,stroke:#444444,stroke-dasharray:6 4,color:#222222

    web -->|"request, verify, refresh<br/><i>[HTTPS]</i>"| authRes
    authRes -->|usa| otp
    authRes -->|usa| accounts
    authRes -->|usa| tokens
    otp -->|"envia código"| mailer
    mailer -->|"<i>[SMTP]</i>"| mail
    otp -->|"EMAIL_OTP<br/><i>[JDBC]</i>"| db
    accounts -->|"ACCOUNT<br/><i>[JDBC]</i>"| db
    tokens -->|"REFRESH_TOKEN<br/><i>[JDBC]</i>"| db
```

O `JWT Guard`, que valida o Bearer nas rotas `/me` e `/admin`, está em 4.4.

### 4.4 Componentes: onboarding e catálogo (nível 3)

```mermaid
%%{init: {"flowchart": {"wrappingWidth": 260}}}%%
flowchart TB
    classDef container fill:#438dd5,stroke:#2e6295,color:#ffffff
    classDef ext fill:#999999,stroke:#6b6b6b,color:#ffffff
    classDef component fill:#85bbf0,stroke:#5d82a8,color:#000000

    web["<b>Core Web</b><br/><i>[Container: React SPA]</i><br/>Cliente HTTP"]:::container
    bucket[("<b>Bucket de documentos</b><br/><i>[Container: OCI<br/>Object Storage]</i><br/>Privado")]:::container
    cnpjapi["<b>Consulta de CNPJ</b><br/><i>[Sistema externo]</i><br/>Opcional"]:::ext

    subgraph api["Management API  <i>[Container: Quarkus]</i>"]
        guard["<b>JWT Guard</b><br/><i>[Component:<br/>quarkus-smallrye-jwt]</i><br/>Autentica e autoriza<br/>por role"]:::component
        meRes["<b>OnboardingResource</b><br/><i>[Component: JAX-RS /me]</i><br/>Empresa, schema dinâmico,<br/>valores, submit"]:::component
        adminRes["<b>AdminResource</b><br/><i>[Component: JAX-RS /admin]</i><br/>CRUD do catálogo,<br/>revisão, auditoria"]:::component
        onb["<b>OnboardingService</b><br/><i>[Component: CDI]</i><br/>Estados da empresa,<br/>validação por rules,<br/>completude, revisão"]:::component
        catalog["<b>CatalogService</b><br/><i>[Component: CDI]</i><br/>Tipos, passos, atributos.<br/>Monta o schema<br/>do formulário"]:::component
        cnpj["<b>CnpjService</b><br/><i>[Component: CDI]</i><br/>DV alfanumérico,<br/>unicidade, consulta<br/>opcional"]:::component
        docs["<b>DocumentStorageService</b><br/><i>[Component: S3Presigner,<br/>S3Client]</i><br/>Presign PUT e GET, HEAD,<br/>prefixo por empresa.<br/>Já existe e evolui"]:::component
        audit["<b>AuditService</b><br/><i>[Component: CDI]</i><br/>Registra quem<br/>mudou o quê"]:::component
    end
    style api fill:#ffffff,stroke:#444444,stroke-dasharray:6 4,color:#222222

    web -->|"Bearer JWT<br/><i>[HTTPS]</i>"| guard
    guard -->|"role COMPANY_OWNER"| meRes
    guard -->|"role PLATFORM_ADMIN"| adminRes
    meRes -->|usa| onb
    adminRes -->|"revisão"| onb
    adminRes -->|"grava catálogo"| catalog
    adminRes -->|"registra mudanças"| audit
    onb -->|"lê schema e rules"| catalog
    onb -->|"valida CNPJ"| cnpj
    onb -->|"upload e confirmação"| docs
    cnpj -->|"consulta<br/><i>[HTTPS]</i>"| cnpjapi
    docs -->|"presign e HEAD<br/><i>[S3-compat]</i>"| bucket
```

Todos os serviços persistem no Autonomous Database via Panache (setas omitidas para legibilidade; ver 4.2).

Pacotes sugeridos em `com.ecolchain.api`: `auth`, `onboarding`, `catalog`, `documents` (o serviço atual muda para cá) e `common`. Os padrões de backend que o `AGENTS.md` espera desta proposta: pacotes (acima), erro (D13) e auth (D1 a D4 e D11).

O diagrama de deployment fica para quando a hospedagem da API for definida (pergunta 9). Os fluxos dinâmicos estão na seção 5.

## 5. Fluxos

### 5.1 Sign-up e sign-in (mesmo caminho)

```mermaid
sequenceDiagram
    autonumber
    actor U as Representante legal
    participant W as Core Web (SPA)
    participant A as Management API
    participant D as Autonomous DB
    participant E as OCI Email Delivery

    U->>W: informa o e-mail
    W->>A: POST /auth/otp/request {email}
    A->>D: grava OTP (HMAC, TTL 10 min, 0 tentativas)
    A->>E: SMTP: envia código (após o commit)
    A-->>W: 202, igual exista ou não a conta
    E-->>U: e-mail com código de 6 dígitos
    U->>W: digita o código
    W->>A: POST /auth/otp/verify {email, code}
    A->>D: valida HMAC, TTL e tentativas e consome o OTP
    opt e-mail novo
        A->>D: cria ACCOUNT (EMAIL_VERIFIED)
    end
    A-->>W: 200 {accessToken} + Set-Cookie do refresh (HttpOnly)
    W->>A: GET /me (Bearer)
    A-->>W: conta, empresa e nextStep
    Note over W: nextStep define a tela: COMPANY_FORM, ONBOARDING, AWAITING_REVIEW ou HOME
    opt ainda sem empresa
        W->>A: GET /public/profile-types
        A-->>W: tipos ativos
        W->>A: PUT /me/company {cnpj, razaoSocial, nomeCompleto, profileType, aceites}
        A->>D: valida CNPJ (DV e único), cria COMPANY e o membro OWNER
    end
```

### 5.2 Documento (presign → upload direto → confirmação)

```mermaid
sequenceDiagram
    autonumber
    participant W as Core Web (SPA)
    participant A as Management API
    participant B as Bucket OCI
    participant D as Autonomous DB

    W->>A: GET /me/onboarding
    A-->>W: passos, atributos (DATA ou DOCUMENT), valores e status
    W->>A: POST /me/onboarding/documents/{code}/upload-url {filename, contentType, contentLength}
    A->>A: confere o atributo no fluxo da empresa, extensão e tamanho
    A->>D: grava DOCUMENT (PENDING) com a objectKey
    A-->>W: URL PUT pré-assinada (10 min) e objectKey com o prefixo da empresa
    W->>B: PUT do arquivo, direto no bucket
    W->>A: POST /me/onboarding/documents/{code}/confirm {objectKey}
    A->>B: HEAD (existe? tamanho confere?)
    A->>D: marca DOCUMENT como CONFIRMED e grava o valor do atributo = objectKey
    A-->>W: 200
    W->>A: POST /me/onboarding/submit
    A->>D: confere os obrigatórios e muda para UNDER_REVIEW
```

### 5.3 Estados da empresa

```mermaid
stateDiagram-v2
    [*] --> PENDING_DOCUMENTS: PUT /me/company
    PENDING_DOCUMENTS --> UNDER_REVIEW: submit com obrigatórios ok
    UNDER_REVIEW --> CHANGES_REQUESTED: admin pede ajustes
    CHANGES_REQUESTED --> UNDER_REVIEW: submit
    UNDER_REVIEW --> APPROVED: admin aprova
    UNDER_REVIEW --> REJECTED: admin rejeita
    APPROVED --> CHANGES_REQUESTED: admin reabre
    APPROVED --> SUSPENDED: admin suspende
```

O `nextStep` é calculado no servidor a partir desses estados. O SPA não carrega regra de fluxo.

## 6. Perguntas em aberto

1. **Lista de dados e documentos por tipo de perfil.** Não chegou. Pode colar? Com ela eu semeio o catálogo; sem ela, só os 6 tipos.
2. **Sem senha (D1)?** E-mail + código para entrar sempre, com refresh de 7 dias. Alguma razão para exigir senha ou 2FA já no MVP (principalmente para o admin)?
3. **Aprovação manual (D12).** Enquanto a empresa está em `PENDING_DOCUMENTS` ou `UNDER_REVIEW`, ela já pode usar alguma coisa da plataforma ou só vê o status?
4. **Troca de tipo de perfil.** Proposta: livre até submeter; depois só o admin troca. Ok?
5. **Mudou a regra com empresas já aprovadas** (novo documento obrigatório)? Proposta: ninguém é afetado sozinho, e o admin usa "reabrir" por empresa.
6. **Um tipo por empresa?** Hoje sim (também há 1 papel por carteira on-chain). Uma cooperativa que também transporta teria 2 cadastros?
7. **Nome completo e razão social:** confirmo pedir os dois, com a razão social pré-preenchida pela consulta de CNPJ quando disponível?
8. **Rótulos em vários idiomas** (o Core Web tem pt-BR, en-US e es-ES)? Proposta: só pt-BR no MVP.
9. **Hospedagem e domínio da API.** Condiciona o cookie de refresh (D2), o CORS e como a chave chega ao deploy.
10. **Chave JWT.** Confirmo: gerada localmente por script, fora do git e injetada no deploy? Ou prefere versionar um par só de dev?

## 7. Entrega em fatias

Cada fatia começa pelo contrato OpenAPI (API-first, como definido no `AGENTS.md`), depois as interfaces geradas, depois a implementação e os testes.

1. **Auth:** migrations de identidade, OTP com Mailer, JWT (com o script de geração de chaves), `/auth/*` e `/me`, com testes.
2. **Catálogo e cadastro:** catálogo e endpoints de admin, `PUT /me/company`, schema dinâmico, atributos `DATA`, seed dos 6 tipos (Gerador, Transportador, Cooperativa, CleanTech, Recicladora, Higienizadora) por migration.
3. **Documentos e revisão:** entidade `DOCUMENT`, presign com prefixo e checagem de dono (substitui o `DevDocumentResource`), `confirm`, `submit` e `review`.
4. **Em paralelo:** hospedagem da API no infra, aplicar o módulo de Email Delivery e uma coleção Bruno com os fluxos.

---

## Anexo A. Modelo configurável

```mermaid
erDiagram
    ACCOUNT ||--o{ COMPANY_MEMBER : participa
    COMPANY ||--o{ COMPANY_MEMBER : tem
    PROFILE_TYPE ||--o{ COMPANY : classifica
    PROFILE_TYPE ||--o{ ONBOARDING_STEP : define
    ONBOARDING_STEP ||--o{ STEP_ATTRIBUTE : agrupa
    ATTRIBUTE_DEFINITION ||--o{ STEP_ATTRIBUTE : "entra em"
    COMPANY ||--o{ COMPANY_ATTRIBUTE_VALUE : preenche
    ATTRIBUTE_DEFINITION ||--o{ COMPANY_ATTRIBUTE_VALUE : "valor de"
    COMPANY ||--o{ DOCUMENT : envia
    ATTRIBUTE_DEFINITION ||--o{ DOCUMENT : "referente a"

    ACCOUNT {
        uuid id PK
        string email UK "minúsculo"
        string full_name "nome completo do representante"
        string role "COMPANY_OWNER ou PLATFORM_ADMIN"
        string status "EMAIL_VERIFIED, ACTIVE, BLOCKED"
    }
    COMPANY_MEMBER {
        uuid account_id FK
        uuid company_id FK
        string member_role "OWNER, único por ora"
        string terms_version
        timestamp terms_accepted_at
    }
    COMPANY {
        uuid id PK
        string cnpj UK "14 caracteres alfanuméricos, único entre as não rejeitadas"
        string legal_name "razão social"
        uuid profile_type_id FK
        string status "ver 5.3"
    }
    PROFILE_TYPE {
        uuid id PK
        string code UK "GERADOR, COOPERATIVA, ..."
        string name
        boolean active
        number position
    }
    ONBOARDING_STEP {
        uuid id PK
        uuid profile_type_id FK
        string code
        string title
        number position
    }
    ATTRIBUTE_DEFINITION {
        uuid id PK
        string code UK "snake_case estável"
        string kind "DATA ou DOCUMENT"
        string label
        string help
        json rules "input, regex, maxLength, allowedExtensions, maxBytes"
        boolean active
    }
    STEP_ATTRIBUTE {
        uuid step_id FK
        uuid attribute_id FK
        boolean required
        number position
    }
    COMPANY_ATTRIBUTE_VALUE {
        uuid company_id FK
        uuid attribute_id FK
        string value "texto ou chave do objeto no bucket"
        string review_status "PENDING, ACCEPTED, REJECTED"
        string review_note
    }
    DOCUMENT {
        uuid id PK
        uuid company_id FK
        uuid attribute_id FK
        string object_key UK "chave no bucket, com prefixo da empresa"
        string filename
        string content_type
        number size_bytes
        string status "PENDING, CONFIRMED"
        timestamp confirmed_at
    }
```

Fora do diagrama: `EMAIL_OTP` (e-mail, hash do código, expiração, tentativas, consumido em), `REFRESH_TOKEN` (conta, família, hash, expiração, revogado em, substituído por) e `AUDIT_LOG` (ator, ação, entidade, antes, depois, quando).

Pontos de projeto:

- O **atributo é reutilizável**: o mesmo `licenca_operacao` pode entrar no fluxo de vários tipos, cada um com seu `required` e sua posição. A definição é **global**: mudar `label` ou `rules` afeta todos os tipos que usam o atributo.
- Tirar um atributo do fluxo **não apaga** valores já coletados (ficam para auditoria).
- Mudança de fluxo vale para quem ainda não submeteu. Empresa já aprovada não é afetada, a menos que o admin use "reabrir" (pergunta 5).
- `code` do atributo é a identificação estável (imutável) usada nos endpoints. Não confundir com a `objectKey` do bucket.
- Num atributo `DOCUMENT`, o valor guarda só a `objectKey` (string, como pedido). Os metadados do arquivo (nome, tipo, tamanho, estado do upload) ficam em `DOCUMENT`.
- A regex do admin só roda depois de checar `maxLength` (entrada limitada), para evitar ReDoS.
- **CNPJ reservado por terceiros:** como só provamos o e-mail, alguém poderia cadastrar primeiro o CNPJ de outra empresa. Por isso o CNPJ é único só entre empresas não rejeitadas: o admin rejeita o cadastro indevido e o CNPJ volta a ficar livre. Quem tenta um CNPJ já em uso vê uma mensagem para falar com a Ecolchain.

### Exemplo (ilustrativo): admin define o fluxo de um tipo em uma chamada

```http
PUT /admin/profile-types/COOPERATIVA/flow
```

```json
{
  "steps": [
    {
      "code": "licencas",
      "title": "Licenças e autorizações",
      "attributes": [
        {
          "code": "licenca_operacao",
          "required": true,
          "definition": {
            "kind": "DOCUMENT",
            "label": "Licença de operação",
            "help": "PDF de até 10 MB",
            "rules": { "allowedExtensions": ["pdf"], "maxBytes": 10485760 }
          }
        },
        { "code": "capacidade_mensal_kg", "required": false }
      ]
    }
  ]
}
```

`definition` é opcional: se o atributo já existe e a chamada não traz definição, ele é reaproveitado; se traz, é criado ou atualizado (upsert por `code`). O `PUT` substitui o fluxo inteiro em uma transação, o que permite versionar o JSON de cada tipo no Git e reaplicar em dev e prod. `POST /admin/profile-types` aceita o mesmo `steps`, então criar o tipo e seus atributos também é uma chamada só.

### Exemplo (ilustrativo): o que o SPA recebe em `GET /me/onboarding`

```json
{
  "status": "PENDING_DOCUMENTS",
  "profileType": { "code": "COOPERATIVA", "name": "Cooperativa" },
  "steps": [
    {
      "code": "licencas",
      "title": "Licenças e autorizações",
      "attributes": [
        {
          "code": "licenca_operacao",
          "kind": "DOCUMENT",
          "label": "Licença de operação",
          "help": "PDF de até 10 MB",
          "required": true,
          "rules": { "allowedExtensions": ["pdf"], "maxBytes": 10485760 },
          "value": null,
          "review": null
        }
      ]
    }
  ],
  "progress": { "requiredTotal": 1, "requiredFilled": 0 }
}
```

## Anexo B. Endpoints

Paths em inglês, como o código atual. Esta tabela é o rascunho dos recursos para o contrato OpenAPI, que é escrito **primeiro** (API-first, ver `AGENTS.md`); as interfaces JAX-RS saem do OpenAPI Generator.

**Públicos**

| Método | Path | Descrição |
|---|---|---|
| POST | `/auth/otp/request` | `{email}`. Sempre 202 |
| POST | `/auth/otp/verify` | `{email, code}`. Devolve o access token e o cookie de refresh |
| POST | `/auth/refresh` | Cookie. Rotaciona o refresh e devolve novo access |
| POST | `/auth/logout` | Cookie. Revoga a família de refresh |
| GET | `/public/profile-types` | Tipos ativos, para o select do cadastro (cacheável) |
| GET | `/.well-known/jwks.json` | Chaves públicas (opcional no MVP) |

**Empresa (`COMPANY_OWNER`)**

| Método | Path | Descrição |
|---|---|---|
| GET | `/me` | Conta, empresa e `nextStep` |
| PUT | `/me/company` | Cria a empresa: CNPJ, razão social, nome completo, tipo, declaração e aceite de termos |
| GET | `/me/onboarding` | Schema dinâmico, valores atuais e status |
| PUT | `/me/onboarding/attributes/{code}` | Grava o valor de um atributo `DATA` |
| POST | `/me/onboarding/documents/{code}/upload-url` | Presign PUT |
| POST | `/me/onboarding/documents/{code}/confirm` | Confirma o upload (HEAD) e grava a chave |
| GET | `/me/onboarding/documents/{code}/download-url` | Presign GET |
| POST | `/me/onboarding/submit` | Confere os obrigatórios e envia para revisão |

**Admin (`PLATFORM_ADMIN`)**

| Método | Path | Descrição |
|---|---|---|
| GET, POST, PATCH | `/admin/profile-types[/{code}]` | CRUD de tipos (desativar em vez de apagar). `POST` aceita `steps` |
| PUT | `/admin/profile-types/{code}/flow` | Define passos e atributos de um tipo, de forma declarativa e atômica |
| GET, POST, PUT | `/admin/attributes[/{code}]` | Catálogo de atributos |
| GET | `/admin/companies?status=` | Fila de revisão |
| GET | `/admin/companies/{id}` | Empresa e valores |
| GET | `/admin/companies/{id}/documents/{code}/download-url` | Presign GET para revisar |
| POST | `/admin/companies/{id}/review` | `{decision, note, items[]}` com `APPROVE`, `REQUEST_CHANGES` ou `REJECT` |
| GET | `/admin/audit-log` | Trilha de mudanças de catálogo e revisões |

## Anexo C. Segurança, chaves e e-mail

### C.1 Chaves JWT no MVP

JWT não precisa de certificado X.509, só do par de chaves. O `.gitignore` já bloqueia `*.pem` e `*.key`.

```bash
mkdir -p keys
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out keys/jwt-private.pem
openssl pkey -in keys/jwt-private.pem -pubout -out keys/jwt-public.pem
```

Configuração (exemplo; validar os caminhos na implementação). Os caminhos vêm do `.env` (gitignored), como as credenciais S3:

```properties
mp.jwt.verify.publickey.location=${JWT_PUBLIC_KEY_LOCATION}
mp.jwt.verify.issuer=ecolchain-management-api
mp.jwt.verify.audiences=ecolchain-app
smallrye.jwt.sign.key.location=${JWT_PRIVATE_KEY_LOCATION}
smallrye.jwt.new-token.issuer=ecolchain-management-api
smallrye.jwt.new-token.audience=ecolchain-app
smallrye.jwt.new-token.lifespan=900
```

- **Local:** `keys/` na raiz do projeto, gerado por um script. Em dev e test, se nenhuma chave estiver configurada, o Quarkus gera um par efêmero (os tokens morrem a cada restart), por isso o par fixo.
- **Deploy:** a chave entra por arquivo montado (secret ou volume) ou por variável de ambiente. **Nunca dentro do jar ou da imagem**, e a privada nunca no git: quem a tem emite token como qualquer conta, inclusive admin (por isso `/admin/**` também confere o papel no banco).
- **Rotação (evolução):** `kid` no header já no MVP; na troca, o JWKS publica as duas chaves por um tempo. Depois, OCI Vault.

### C.2 E-mail (OCI Email Delivery)

A infra (domínio, DKIM, sender, credencial SMTP) e a configuração do Mailer já estão sendo tratadas no módulo `oci/email-delivery` e no README da API. Para o fluxo de auth, o que importa:

- O `From` precisa ser exatamente o Approved Sender (`ecolchain@ecolchain.com`).
- **Limite de 200 e-mails/24 h e 10/min** na tenancy: cada login sem refresh válido gasta um e-mail, e quem esgota a cota derruba o login de todo mundo. Mitigações: refresh de 7 dias, cooldown por e-mail e por IP, teto global diário com erro amigável quando perto do limite e, antes de abrir ao público, Cloudflare Turnstile no `request`. Aumento de limite exige SPF e DKIM (ver README do infra).
- O envio acontece **depois do commit** da transação, e o `request` nunca revela se o e-mail existe.
- Em `quarkus:dev` e nos testes o Mailer vem em mock (o código aparece no console e o `MockMailbox` lê o e-mail nos testes). Num deploy `dev` o envio é real e gasta a cota.
- O e-mail só leva o código, a validade e "se não foi você, ignore": sem link, para não treinar o usuário a clicar.
- Evolução: a credencial SMTP hoje fica no usuário do Terraform; o ideal é um usuário IAM dedicado só com `use email-family`.

### C.3 Parâmetros iniciais e checklist

Tudo em `application.properties` via `@ConfigMapping(prefix = "app.auth")`, como `DocumentsConfig` já faz.

| Item | Valor inicial |
|---|---|
| OTP | 6 dígitos (`SecureRandom`), validade 10 min, 5 tentativas, uso único. Guardado como HMAC-SHA256 com pepper de config, comparado em tempo constante |
| Reenvio | Cooldown 60 s; máx. 5 por hora por e-mail; teto por IP |
| Access JWT | 15 min, RS256, claims `iss`, `aud`, `sub` (id da conta), `groups`, `jti`; `kid` no header |
| Refresh | 7 dias (30 dias absolutos), 256 bits aleatórios, SHA-256 no banco, rotativo; reuso revoga a família |

Checklist:

- Resposta única para código errado, expirado ou inexistente. Conta `BLOCKED` não recebe token.
- Verificador com algoritmo fixo (sem `none`), validando `iss`, `aud` e `exp`.
- CORS restrito às origens do SPA. Refresh e logout por cookie exigem origem permitida e `SameSite=Strict`.
- Deny by default: tudo exige JWT, exceto `/auth/**`, `/public/**`, health e JWKS.
- Documentos: bucket privado (já é), URLs curtas, tipo e tamanho validados, chave com prefixo da empresa. Sem varredura de malware no MVP: só admin e o titular baixam, por URL assinada.
- Logs JSON (já em uso): nunca registrar OTP, tokens ou e-mail completo.
- Toda mudança de catálogo e toda revisão grava `AUDIT_LOG`.
- LGPD: aceite de termos versionado com data, finalidade e retenção definidas, acesso aos documentos só do titular e do admin.
- Testes: `@QuarkusTest` com `MockMailbox`; validador de CNPJ com o exemplo oficial da Receita (`12.ABC.345/01DE-35`); `ConfigGuardTest` estendido para garantir que nenhuma chave ou segredo entra no repo.

CNPJ: módulo 11 sobre os 12 primeiros caracteres, cada um convertido por `ASCII − 48`, pesos de 2 a 9 da direita para a esquerda. Armazenar como `VARCHAR2(14)` em maiúsculas, nunca como número. A consulta pública é só conveniência e nunca bloqueia: os provedores podem ainda não aceitar o formato alfanumérico.
