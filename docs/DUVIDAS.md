# DUVIDAS.md — dúvidas abertas da implementação

Dúvidas registradas pela sessão de implementação enquanto o usuário estava indisponível. Cada item tem a decisão adotada para não bloquear o trabalho; revise e peça ajuste onde discordar. `- [ ]` = pendente de revisão do usuário.

## Abertas

- [ ] **Consulta pública de CNPJ** — qual provedor usar (ReceitaWS, BrasilAPI, etc.)? Decisão: adapter `CnpjLookup` com implementação desligada por config (`app.cnpj.lookup.enabled=false` por padrão); cadastro nunca bloqueia.
- [ ] **Texto dos Termos de Uso / declaração "sou dono ou representante legal"** — precisa de redação legal. Decisão: `terms_version = "v1-placeholder"`, texto provisório em `docs/features/register-company.md`; trocar quando sair o texto oficial.
- [ ] **Copy e arte do e-mail de OTP (pt-BR/en)** — envio real já funciona (Email Delivery ativo); decisão: template HTML simples com código em destaque, validade 10 min e "se não foi você, ignore", assunto `Seu código Ecolchain` / `Your Ecolchain code`. Revisar arte/copy depois.
- [ ] **Origens CORS locais** — o `ecolchain-management-web` ainda não existe. Decisão: `%dev` permite `http://localhost:5173` e `http://localhost:3000` via config; prod sem origens até o domínio existir.
- [ ] **Número de sócios/diretoria** — `partner_*` e `board_member_*` valem como **1 upload por atributo** (constraint unique `company_id + attribute_id` na tabela `document`): todos os sócios/RGs num único PDF. Se precisar N arquivos por pessoa, vira modelagem nova. Decisão consistente com "um atributo por item".
- [ ] **ArchUnit** para blindar a fronteira hexagonal em teste — decisão: não adicionado no MVP (estrutura por revisão); pode entrar depois.
- [x] ~~**ADB auto-pausada**~~ — descartado: E2E completo rodado contra MGMT_DEV em 2026-10-09 com sucesso.

## Respondidas pelo usuário (referência)

- Login do admin: OTP por e-mail, sem senha — `leandroluz201616@gmail.com`.
- Chaves JWT: dev e prod commitadas em `keys/` (débito técnico registrado).
- Documentos: **um atributo por item**; cada tipo de perfil escolhe quais atributos solicita (editável via endpoint).
- Condicionais ("se comercializa subprodutos" etc.) entram como **opcionais**; motorista/veículo fora do onboarding.
- Pré-aprovação: empresa só vê status; revisão manual do admin.

## Deploy OCI (VM A1.Flex + OCIR + LB)

- [ ] **Hostname prod = `api-prod.ecolchain.com`** (não `api.ecolchain.com`) — confirmado com `api-base.ts`/`AMBIENTES.md` do management-web, que já injeta `NEXT_PUBLIC_API_URL=https://api-prod.ecolchain.com` no build de prod.
- [ ] **Deploy converge por polling**: CI só faz `docker buildx build --push` da tag (`:dev`, `:prod`, `:<git-sha>`); um timer systemd na VM puxa a cada ~2min e sobe o container novo. Sem SSH nem self-hosted runner no caminho do deploy (decisão detalhada no DUVIDAS do ecolchain-infra-general).
- [ ] **Imagem só linux/arm64** — a VM é Ampere A1 (aarch64). O Dockerfile é multi-stage: o stage `maven` roda em `$BUILDPLATFORM` (x86 do runner, rápido) e o runtime `ubi9/openjdk-25-runtime` sai em arm64. Multi-arch no OCIR fica como melhoria futura se aparecer consumer amd64.
- [ ] **`./mvnw verify` roda duas vezes em CI** (step explícito + dentro do Dockerfile com `-DskipTests`). Aceito para manter o Dockerfile auto-suficiente (`docker build` fora da esteira funciona igual); a etapa de testes no Dockerfile é pulada (`-DskipTests`) para não duplicar testes.
- [ ] **CORS por profile**: dev ganhou `https://manage-dev.ecolchain.com` (localhosts mantidos para dev local); prod ganhou `https://manage.ecolchain.com` — o default sem profile continua sem origens. Credenciais já estavam ligadas (cookie de refresh).
- [ ] **Mailer dev mockado** via `QUARKUS_MAILER_MOCK=true` no container `api-dev` (env file gerado pelo Ansible); prod usa `QUARKUS_MAILER_USERNAME/PASSWORD` vindo do remote state `oci/email-delivery`. Senha SMTP nova fica como pendência caso o sender ainda não exista.
- [ ] **Secrets novos da esteira** (bot não consegue `gh secret set`): `OCIR_USERNAME` (`<namespace>/<usuario>`) e `OCIR_AUTH_TOKEN` — saem de `terraform output` no root `management-api-runtime` do infra-general.
- [ ] **Health check**: LB bate em `GET /q/health` (smallrye-health já no pom, path já público no `AuthFilter`); smoke da esteira cobre `/public/profile-types` + preflight CORS credenciado.
