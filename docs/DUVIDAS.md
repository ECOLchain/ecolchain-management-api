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
