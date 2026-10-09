# Login por OTP de e-mail (sem senha)

`POST /auth/otp/request` → sempre 202 (não revela se o e-mail existe).
`POST /auth/otp/verify` → 200 + `SessionResponse` + cookie de refresh.

## Regras (proposta C.3)
- Código: 6 dígitos via `SecureRandom`, guardado como **HMAC-SHA256(code, pepper)** — nunca em claro.
- TTL 10min, máx 5 tentativas, uso único (`consumed_at`).
- Cooldown de reenvio: 60s por e-mail.
- Rate limit: 5/h por e-mail, 20/h por IP (X-Forwarded-For), cap diário global 180 (< quota 200/24h do OCI Email Delivery).
- Resposta genérica nos erros de código: `OTP_INVALID` para errado/expirado/esgotado.
- Nunca loga código nem e-mail completo (máscara `p***@dominio`).

## Implementação
- `RequestOtpUseCase` / `VerifyOtpUseCase` (application) + `OtpStore`/`OtpMailer`/`AccountStore`/`RefreshTokenStore`/`AccessTokenIssuer` (portas).
- `EmailOtpEntity` (Panache) persistido em `EMAIL_OTP` (migration `V2__identity.sql`).
- E-mail via `SmtpOtpMailer` + Qute `templates/otp.html` (pt/en por `Accept-Language`).
- E-mail inexistente: conta nasce `COMPANY_OWNER`/`EMAIL_VERIFIED` no verify (registro de empresa é passo seguinte). Admin seeded `leandroluz201616@gmail.com` = `PLATFORM_ADMIN`.

## Teste/dev
- `%test`/`%dev`: `app.dev-tools.mailbox-enabled=true` + `quarkus.mailer.mock=true` (test) expõe `GET /dev-mailbox?to=<email>` com o HTML enviado — usado no E2E offline e pelo frontend.
