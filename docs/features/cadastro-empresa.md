# Cadastro da empresa

`PUT /me/company` (autenticado): cria ou atualiza a empresa do usuário logado.

## Regras
- `cnpj` alfanumérico: `CnpjValidator` — módulo 11 sobre os 12 primeiros chars (ASCII−48), pesos 2–9 da direita p/ esquerda; guardado maiúsculo sem máscara (14 chars). Oficial de teste: `12.ABC.345/01DE-35`. Único entre empresas não rejeitadas (índice parcial `uq_company_cnpj_active` + check na use case).
- `tiposPerfil`: 1..N códigos de `PROFILE_TYPE` ativos → `COMPANY_PROFILE` (union dos fluxos).
- `declaracaoRepresentante` obrigatória; `versaoTermos` default = `app.auth.terms-version`; `termsAcceptedAt` gravado.
- `nomeCompleto` atualiza `account.full_name`.
- Só editável em `PENDING_DOCUMENTS`, `CHANGES_REQUESTED`, `PENDING_UPDATE` — senão `COMPANY_LOCKED`.
- CNPJ em uso → `CNPJ_IN_USE`; inválido → `CNPJ_INVALID`; perfil inexistente → `PROFILE_TYPE_INVALID`.
- Membro criador vira `company_member(role=OWNER)`; account vira ativa para onboarding.

## Status
`PENDING_DOCUMENTS → UNDER_REVIEW → APPROVED | REJECTED | CHANGES_REQUESTED` (+ `SUSPENDED`, `PENDING_UPDATE`). `nextStep` calculado no servidor em `GET /me`.
