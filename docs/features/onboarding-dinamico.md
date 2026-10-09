# Onboarding dinâmico

`GET /me/onboarding`: união dos fluxos dos tipos de perfil da empresa —
passos mesclados por `code` (mesmo código = mesmo passo; atributo required vence
optional na união). Rótulos por `Accept-Language` (pt default, en se preferido).

- `progresso`: total de obrigatórios vs preenchidos (DATA com valor / DOCUMENT `UPLOADED`).
- `PUT /me/onboarding/attributes/{code}`: salva valor se o atributo DATA está no fluxo da empresa; valida `rules` (`maxLength`, `regex`) → `ATTRIBUTE_VALUE_INVALID`.
- `POST /me/onboarding/submit`: exige todos os requeridos preenchidos → `UNDER_REVIEW` + `submitted_at`; senão `SUBMIT_INCOMPLETE` com a lista de códigos faltantes.

Configuração no banco (`V3__catalog.sql` seeds do Anexo D): `profile_type`, `onboarding_step`, `attribute_definition`, `step_attribute` — nada hardcoded.
