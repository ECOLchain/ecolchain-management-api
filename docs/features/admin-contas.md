# Contas de administradores (admin-contas)

`GET /admin/accounts` → lista contas `PLATFORM_ADMIN`.
`POST /admin/accounts` `{email, nomeCompleto, telefone}` → 201 + `AdminAccount`; 409 `ACCOUNT_IN_USE` se o e-mail já tem conta.

## Regras

- Só `PLATFORM_ADMIN` chama (re-checado no banco por request, como todo `/admin/**`).
- O novo admin nasce `PLATFORM_ADMIN`/`EMAIL_VERIFIED` — no primeiro login por OTP já entra como admin (não passa pelo onboarding de empresa).
- `email` normalizado (trim + lowercase). `nomeCompleto` e `telefone` obrigatórios (400 `VALIDATION`).
- Criação auditada: `ADMIN_ACCOUNT_CREATED` em `audit_log` (ator = admin que criou).
- `telefone` novo campo de `account` (migration `V6__account_phone.sql`), exposto em `ContaInfo.telefone` do `/me` e da sessão.

## Implementação

- `AdminController.listAdminAccounts` / `createAdminAccount` (`@Transactional`, contrato `AdminApi` gerado do OpenAPI).
- `AccountStore.listByRole` (porta nova) + `PanacheAccountStore`.
- Seed do primeiro admin segue sendo a migration (`leandroluz201616@gmail.com`); este endpoint só cria admins adicionais — não há delete/promote/demote (evolução futura).
