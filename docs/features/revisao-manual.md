# Revisão manual (admin)

- `GET /admin/companies?status=&pagina=&tamanho=` — fila.
- `GET /admin/companies/{id}` — dados + contato do owner + valores de atributos + docs.
- `GET /admin/companies/{id}/documents/{code}/download-url` — baixa doc.
- `POST /admin/companies/{id}/review` `{decisao, nota, itens[]}`:
  - `APPROVE` (só de `UNDER_REVIEW`) → `APPROVED`
  - `REQUEST_CHANGES` (de `UNDER_REVIEW`, nota obrigatória) → `CHANGES_REQUESTED` + `review_notes`
  - `REJECT` (de `UNDER_REVIEW`/`CHANGES_REQUESTED`, nota obrigatória) → `REJECTED` (CNPJ liberado)
  - `SUSPEND` (de `APPROVED`) → `SUSPENDED`
- Novo atributo obrigatório no fluxo → empresas `APPROVED` com aquele perfil viram `PENDING_UPDATE` (re-submetem → `UNDER_REVIEW`).
- Toda ação admin em `audit_log` (`GET /admin/audit-log`).
- `nextStep`: `PENDING_DOCUMENTS/CHANGES_REQUESTED/PENDING_UPDATE → ONBOARDING`; `UNDER_REVIEW → AWAITING_REVIEW`; `APPROVED → HOME`; `REJECTED/SUSPENDED → telas informativas`.
