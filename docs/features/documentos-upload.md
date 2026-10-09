# Upload de documentos (direto ao bucket)

Fluxo presign em 3 chamadas (contrato):
1. `POST /me/onboarding/documents/{code}/upload-url` `{nomeArquivo, contentType, tamanhoBytes}` → cria intenção `DOCUMENT(status=PENDING_UPLOAD)` e devolve URL PUT assinada (key `docs/{uuid}-{nome}`).
2. SPA faz `PUT` direto no S3-compat OCI (headers assinados: Content-Type + Content-Length).
3. `POST /me/onboarding/documents/{code}/confirm` `{objectKey}` → `HEAD` no bucket; existe → `UPLOADED`, senão `DOCUMENT_UPLOAD_FAILED`.

- `GET /me/onboarding/documents/{code}/download-url`: dono relê o próprio doc (UPLOADED/ACCEPTED).
- `GET /admin/companies/{id}/documents/{code}/download-url`: revisor baixa para análise.
- Regras do arquivo: mesma allow-list do `DocumentStorageService` (ext→contentType, tamanho máx `app.docs.max-size-bytes`, nome sanitizado). `extensoesPermitidas` em `rules` do atributo é validado no fluxo futuro; hoje a allow-list global governa.
- Adapter: `S3ObjectStorage` (documents) embrulha `DocumentStorageService` + `S3Client.headObject`.
