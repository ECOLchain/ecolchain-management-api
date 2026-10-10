# Revisão manual (admin)

- `GET /admin/companies?status=&pagina=&tamanho=` — fila.
- `GET /admin/companies/{id}` — dados + contato do owner + valores de atributos + docs.
- `GET /admin/companies/{id}/documents/{code}/download-url` — baixa doc.
- `POST /admin/companies/{id}/review` `{decisao, nota, itens[]}`:
  - `APPROVE` (só de `UNDER_REVIEW`) → `APPROVED`
  - `REQUEST_CHANGES` (de `UNDER_REVIEW`, nota obrigatória) → `CHANGES_REQUESTED` + `review_notes`
  - `REJECT` (de `UNDER_REVIEW`/`CHANGES_REQUESTED`, nota obrigatória) → `REJECTED` (CNPJ liberado)
  - `SUSPEND` (de `APPROVED`) → `SUSPENDED`
- `itens[]` = revisão por item `{atributo, decisao: ACCEPT|REJECT, nota?}` aplicada
  junto com a decisão da empresa, em qualquer uma delas:
  - DOCUMENT → `document.status` ACCEPTED/REJECTED + `review_note` (o dono revê o
    rejeitado e reenvia — `myDownloadUrl` aceita REJECTED);
  - DATA → `company_attribute_value.review_status`/`review_note` (editar o valor
    via `upsert` devolve o item a PENDING p/ novo ciclo);
  - atributo fora do fluxo da empresa ou sem conteúdo → `REVIEW_ITEM_INVALID`.
  Resposta = detalhe completo da empresa (mesmo payload de `GET /admin/companies/{id}`).
- Reenvio pós-`CHANGES_REQUESTED`: item ACCEPTED conta como preenchido (doc ou dado);
  REJECTED obrigatório bloqueia até correção.
- Novo atributo obrigatório no fluxo → empresas `APPROVED` com aquele perfil viram `PENDING_UPDATE` (re-submetem → `UNDER_REVIEW`).
- Toda ação admin em `audit_log` (`GET /admin/audit-log`).
- `nextStep`: `PENDING_DOCUMENTS/CHANGES_REQUESTED/PENDING_UPDATE → ONBOARDING`; `UNDER_REVIEW → AWAITING_REVIEW`; `APPROVED → HOME`; `REJECTED/SUSPENDED → telas informativas`.
