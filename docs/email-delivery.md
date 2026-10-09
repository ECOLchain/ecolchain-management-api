# Envio de e-mail — OCI Email Delivery (SMTP)

> **Status:** aplicado e validado · envio real testado em 09/10/2026 (resposta `250 Ok`)
> **Infra:** módulo `infra/terraform/oci/email-delivery` no repo `ecolchain-infra-general` (Email Domain `ecolchain.com`, DKIM via Cloudflare, Approved Sender e credencial SMTP já criados e `ACTIVE`)

A API envia e-mail transacional (ex.: código de verificação de cadastro) por SMTP
usando `quarkus-mailer`. **Sem distinção dev/prod no início**: a mesma
configuração vale para todos os profiles e o envio é real — só `test` usa mock.

Fluxo: app Quarkus (`quarkus-mailer`) → SMTP `465`/TLS → OCI Email Delivery →
caixa do usuário.

## 1. Conexão — valores reais (produção = único ambiente por ora)

| Campo | Valor |
|---|---|
| Servidor SMTP | `smtp.email.sa-saopaulo-1.oci.oraclecloud.com` |
| Porta | `465` (TLS implícito — a OCI exige TLS) |
| Autenticação | `SMTP AUTH PLAIN` |
| Usuário SMTP | `ocid1.user.oc1..aaaaaaaaxjsseo253ekj4bagjs5r7fwjwiyyqacapmj2ldzt7l3t27ws7d3a@ocid1.tenancy.oc1..aaaaaaaaosg4wcbgy53ear3crwyebrf4pxrneclac6eeqanradeinycnmuka.am.com` |
| Senha SMTP | no `.env` da raiz (já preenchido, gitignored) — **não commitada** |
| Remetente (`From`) | `ECOLchain <ecolchain@ecolchain.com>` |
| Região | `sa-saopaulo-1` (sender e endpoint são regionais; precisam bater) |
| DKIM | `ecolchain-gru-20261008._domainkey.ecolchain.com` → CNAME no Cloudflare (`ACTIVE`) |
| Limite atual | 200 e-mails/24 h e 10/min (trial da tenancy) |

> O `From` precisa ser **exatamente** o Approved Sender (`ecolchain@ecolchain.com`).
> Qualquer outro endereço é rejeitado com `535 Authorization failed: address not authorized`.

## 2. Onde cada coisa está

- **Dependência**: `io.quarkus:quarkus-mailer` já está no `pom.xml`.
- **Config não-secreta**: commitada em `src/main/resources/application.properties`:

  ```properties
  quarkus.mailer.host=smtp.email.sa-saopaulo-1.oci.oraclecloud.com
  quarkus.mailer.port=465
  quarkus.mailer.tls=true
  quarkus.mailer.login=REQUIRED
  quarkus.mailer.from=ECOLchain <ecolchain@ecolchain.com>
  quarkus.mailer.mock=false   # envio real em todos os profiles (sem distincao dev/prod)
  # %test.quarkus.mailer.mock=true  -> testes nao saem para a rede
  ```

- **Credenciais**: `.env` na raiz do projeto (gitignored; o Quarkus lê `.env`
  automaticamente no startup, em `quarkus:dev` e no jar):

  ```shell script
  QUARKUS_MAILER_USERNAME=ocid1.user.oc1..aaaaaaaaxjsseo253ekj4bagjs5r7fwjwiyyqacapmj2ldzt7l3t27ws7d3a@ocid1.tenancy.oc1..aaaaaaaaosg4wcbgy53ear3crwyebrf4pxrneclac6eeqanradeinycnmuka.am.com
  QUARKUS_MAILER_PASSWORD=<senha real está no .env; não vai para o git>
  ```

  Notas sobre o `.env`:
  - É lido como `.properties`: **sem aspas** em volta do valor.
  - A senha real contém `$` — o Quarkus lê literal (só `${` viraria expressão, e
    a senha não tem `${`). Mas **não faça `source .env` no bash**: o `$` seria
    expandido. Para exportar manualmente, use aspas simples.
  - Fonte alternativa dos dois valores (o `.env` já está preenchido):

    ```shell script
    cd ../ecolchain-infra-general
    set -a; source infra/terraform/oci/tfstate-backend/env/backend.env; set +a
    terraform -chdir=infra/terraform/oci/email-delivery output -raw smtp_username
    terraform -chdir=infra/terraform/oci/email-delivery output -raw smtp_password
    ```

  - No deploy, as mesmas variáveis vão como env vars do processo
    (`QUARKUS_MAILER_USERNAME` / `QUARKUS_MAILER_PASSWORD`).

## 3. Enviando na app

```java
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class VerificationMailService {

    @Inject
    Mailer mailer;

    public void sendCode(String to, String code) {
        mailer.send(Mail.withText(to,
            "Seu código de verificação ECOLchain",
            "Seu código é " + code + ". Ele expira em 10 minutos."));
    }
}
```

O `Mailer` bloqueia até o servidor SMTP aceitar a mensagem (aceitar != entregar).
Envie **depois** do commit da transação, para não segurar conexão do banco
durante a chamada SMTP.

## 4. Testes

`%test.quarkus.mailer.mock=true` já está setado: os testes não fazem rede e o
`MockMailbox` captura os envios:

```java
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.MockMailbox;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class VerificationMailServiceTest {

    @Inject
    MockMailbox mailbox;

    @Inject
    VerificationMailService service;

    @BeforeEach
    void init() {
        mailbox.clear();
    }

    @Test
    void sendsVerificationCode() {
        service.sendCode("user@example.com", "123456");

        List<Mail> sent = mailbox.getMessagesSentTo("user@example.com");
        assertEquals(1, sent.size());
        assertTrue(sent.get(0).getText().contains("123456"));
    }
}
```

## 5. Teste manual de envio (sem subir a app)

```shell script
cd ../ecolchain-infra-general
set -a; source infra/terraform/oci/tfstate-backend/env/backend.env; set +a   # backend.env nao tem "export"
TF="terraform -chdir=infra/terraform/oci/email-delivery"
export SMTP_HOST="$($TF output -raw smtp_endpoint)" SMTP_USER="$($TF output -raw smtp_username)" SMTP_PASS="$($TF output -raw smtp_password)" SMTP_FROM="$($TF output -raw sender_email)" SMTP_TO="<seu-email>"

python3 - <<'PY'
import os, ssl, smtplib
from email.message import EmailMessage

host, user, pw = os.environ["SMTP_HOST"], os.environ["SMTP_USER"], os.environ["SMTP_PASS"]
sender, to = os.environ["SMTP_FROM"], os.environ["SMTP_TO"]

msg = EmailMessage()
msg["From"] = f"ECOLchain <{sender}>"
msg["To"] = to
msg["Subject"] = "Teste OCI Email Delivery - ECOLchain"
msg.set_content("Teste de envio via OCI Email Delivery.")

with smtplib.SMTP_SSL(host, 465, context=ssl.create_default_context(), timeout=30) as s:
    s.login(user, pw)
    s.mail(sender)
    s.rcpt(to)
    print(s.data(msg.as_bytes()))   # (250, b'Ok') = aceito pelo servidor
PY
```

`250 Ok` = a OCI **aceitou** a mensagem (aceitar != entregar; confira caixa de
entrada/spam e, no Gmail, "Mostrar original" para ver `DKIM/SPF/DMARC: PASS`).
Validado em 09/10/2026 com envio real.

## 6. Cuidados para o fluxo de cadastro com código

- Código de 6 dígitos com `SecureRandom`, validade ~10 min, uso único.
- Limite de tentativas (~5); invalida o código depois de estourar.
- Guardar só o hash (HMAC-SHA256 com segredo do servidor) e comparar em tempo
  constante (`MessageDigest.isEqual`).
- **Rate limit de reenvio** por e-mail/IP (ex.: 1/min) — a tenancy tem teto de
  200 e-mails/24 h e 10/min; sem isso o endpoint vira relay de spam e queima a
  reputação do remetente.
- Resposta igual quando o e-mail já existe (evita enumeração de usuários).
- Aumento de limite exige SPF+DKIM (já configurados) e pedido à Oracle — ver
  README do repo de infra.

## 7. Troubleshooting

| Sintoma | Causa provável |
|---|---|
| `535 Authentication credentials invalid` | `.env` não carregou ou valor corrompido (aspas em volta do valor; `source` do bash expandiu o `$` da senha) |
| `535 Authorization failed: address not authorized` | `From` diferente de `ecolchain@ecolchain.com`; sender recém-criado ainda propagando (retry com backoff); região errada |
| E-mail aceito mas cai em spam / não chega | DKIM fora de `ACTIVE` (Console → Developer Services → Email Delivery → Email Domains → `ecolchain.com`) |
| Envios recusados em volume | Limite da tenancy (200/24 h, 10/min) |
| E-mail só aparece no console da app | Mock ativo — só `test` deveria mockar; confira `quarkus.mailer.mock` |

## 8. Rotação da credencial SMTP

A credencial é o recurso `oci_identity_smtp_credential.app` no módulo
`infra/terraform/oci/email-delivery`. Para rotacionar: `terraform taint` +
`apply` gera uma nova senha (cada usuário OCI permite no máx. 2 credenciais
SMTP — o taint remove a antiga primeiro). Depois atualize
`QUARKUS_MAILER_PASSWORD` no `.env`/deploy.
