# Catálogo admin

- `GET/POST /admin/profile-types`, `GET/PATCH /admin/profile-types/{code}` — metadados; `PATCH ativo=false` bloqueado se em uso (`PROFILE_TYPE_IN_USE`).
- `PUT /admin/profile-types/{code}/flow` — substitui fluxo inteiro (passos+atributos, ordem por posição); atributos inline (`definicao`) fazem upsert em `attribute_definition`; marca `APPROVED` relacionadas como `PENDING_UPDATE`.
- `GET/POST /admin/attributes`, `PUT /admin/attributes/{code}` — definições (`kind` DATA/DOCUMENT, labels pt/en, `rules` JSON).
- `GET /public/profile-types` — público, só ativos, labels por `Accept-Language`.
