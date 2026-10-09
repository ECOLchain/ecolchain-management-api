# ecolchain-management-api

This project uses Quarkus, the Supersonic Subatomic Java Framework.

If you want to learn more about Quarkus, please visit its website: <https://quarkus.io/>.

## Running the application in dev mode

You can run your application in dev mode that enables live coding using:

```shell script
./mvnw quarkus:dev
```

> **_NOTE:_**  Quarkus now ships with a Dev UI, which is available in dev mode only at <http://localhost:8080/q/dev/>.

## Packaging and running the application

The application can be packaged using:

```shell script
./mvnw package
```

It produces the `quarkus-run.jar` file in the `target/quarkus-app/` directory.
Be aware that it’s not an _über-jar_ as the dependencies are copied into the `target/quarkus-app/lib/` directory.

The application is now runnable using `java -jar target/quarkus-app/quarkus-run.jar`.

If you want to build an _über-jar_, execute the following command:

```shell script
./mvnw package -Dquarkus.package.jar.type=uber-jar
```

The application, packaged as an _über-jar_, is now runnable using `java -jar target/*-runner.jar`.

## Creating a native executable

You can create a native executable using:

```shell script
./mvnw package -Dnative
```

Or, if you don't have GraalVM installed, you can run the native executable build in a container using:

```shell script
./mvnw package -Dnative -Dquarkus.native.container-build=true
```

You can then execute your native executable with: `./target/ecolchain-management-api-1.0.0-SNAPSHOT-runner`

If you want to learn more about building native executables, please consult <https://quarkus.io/guides/maven-tooling>.

## Envio de e-mail (OCI Email Delivery)

A app envia e-mail por SMTP via OCI Email Delivery usando a extensão
`quarkus-mailer`. Remetente único: `ecolchain@ecolchain.com` — é o único
Approved Sender, então o `From` precisa ser exatamente esse, senão a OCI
rejeita a mensagem.

A infra (domínio, DKIM no Cloudflare, sender e credencial SMTP) é criada pelo
Terraform no repo `ecolchain-infra-general`, módulo
`infra/terraform/oci/email-delivery`. Pré-requisito: módulo aplicado (merge
na `main` do infra + aprovação do environment `prod`).

### 1. Obter as credenciais

```shell script
cd ../ecolchain-infra-general
set -a; source infra/terraform/oci/tfstate-backend/env/backend.env; set +a   # backend.env nao tem "export": set -a exporta para o terraform
terraform -chdir=infra/terraform/oci/email-delivery init -input=false
terraform -chdir=infra/terraform/oci/email-delivery output -raw smtp_username
terraform -chdir=infra/terraform/oci/email-delivery output -raw smtp_password
```

### 2. Dependência

```shell script
./mvnw quarkus:add-extension -Dextensions="mailer"
```

(A extensão ainda não está no `pom.xml`.)

### 3. Configuração

Em `application.properties` (valores não secretos, iguais em todos os
ambientes):

```properties
# --- e-mail: OCI Email Delivery (SMTP, TLS implícito na 465) ---
quarkus.mailer.host=smtp.email.sa-saopaulo-1.oci.oraclecloud.com
quarkus.mailer.port=465
quarkus.mailer.tls=true
quarkus.mailer.login=REQUIRED
quarkus.mailer.from=ECOLchain <ecolchain@ecolchain.com>
```

Usuário/senha ficam fora do repo, como as credenciais S3: no `.env`
(gitignored) ou em variáveis de ambiente no deploy:

```shell script
QUARKUS_MAILER_USERNAME=<saida de smtp_username>
QUARKUS_MAILER_PASSWORD=<saida de smtp_password>
```

Nota: o `.env` é lido como arquivo `.properties` (sem aspas; `\` precisa ser
escrito `\\`).

**Mock x real**: no `./mvnw quarkus:dev` e nos testes o mailer vem em mock por
padrão (o e-mail é impresso no console e não sai). Na app empacotada/deploy
(inclusive com `QUARKUS_PROFILE=dev`) o envio é real. Para sobrescrever use
`QUARKUS_MAILER_MOCK=false` (enviar de verdade no `quarkus:dev`) ou
`QUARKUS_MAILER_MOCK=true` (não enviar num deploy dev) — é propriedade de
runtime.

### 4. Enviando

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

O `Mailer` bloqueia até o servidor SMTP aceitar a mensagem (aceitar !=
entregar).

### 5. Testes

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

### 6. Cadastro com código de verificação — cuidados

- Código de 6 dígitos gerado com `SecureRandom`, validade ~10 min, uso único.
- Limite de tentativas (~5); invalida o código depois de estourar.
- Guardar só o hash (HMAC-SHA256 com segredo do servidor) e comparar em tempo
  constante (`MessageDigest.isEqual`).
- Limitar reenvio por e-mail/IP (ex.: 1/min) — a tenancy tem teto de 200
  e-mails/24h e 10/min.
- Responder igual quando o e-mail já existe (evita enumeração de usuários).
- Enviar o e-mail depois do commit da transação.
- Aumento de limite exige SPF+DKIM e pedido à Oracle (ver README do infra).

### Troubleshooting

| Sintoma | Causa provável |
|---|---|
| `535 Authentication credentials invalid` | Usuário/senha SMTP inválidos (confira o `.env`: sem aspas, `\` escapado) |
| `535 Authorization failed: address not authorized` | `From` diferente de `ecolchain@ecolchain.com`, sender recém-criado ainda não disponível (retry com backoff) ou região diferente |
| Problemas de entrega/spam | DKIM ainda não `ACTIVE` (Console -> Developer Services -> Email Delivery -> Email Domains -> ecolchain.com) |
| Envios recusados em volume | Limite da tenancy (200/24h, 10/min) |
| E-mail só aparece no console da app | Mock ativo (`quarkus:dev`/testes) |

## Related Guides

- SmallRye OpenAPI ([guide](https://quarkus.io/guides/openapi-swaggerui)): Generate OpenAPI schemas and serve Swagger UI for REST API documentation
- REST Jackson ([guide](https://quarkus.io/guides/rest#json-serialisation)): Jackson serialization support for Quarkus REST. This extension is not compatible with the quarkus-resteasy extension, or any of the extensions that depend on it
- OpenTelemetry ([guide](https://quarkus.io/guides/opentelemetry)): Use OpenTelemetry to trace services
- Hibernate ORM with Panache ([guide](https://quarkus.io/guides/hibernate-orm-panache)): Simplified JPA/Hibernate data access layer with active record and repository patterns
- Logging JSON ([guide](https://quarkus.io/guides/logging#json-logging)): Add JSON formatter for console logging
- SmallRye Health ([guide](https://quarkus.io/guides/smallrye-health)): Monitor service health
- JDBC Driver - Oracle ([guide](https://quarkus.io/guides/datasource)): Connect to the Oracle database via JDBC

## Provided Code

### Hibernate ORM

Create your first JPA entity

[Related guide section...](https://quarkus.io/guides/hibernate-orm)


[Related Hibernate with Panache section...](https://quarkus.io/guides/hibernate-orm-panache)


### REST

Easily start your REST Web Services

[Related guide section...](https://quarkus.io/guides/getting-started-reactive#reactive-jax-rs-resources)

### SmallRye Health

Monitor your application's health using SmallRye Health

[Related guide section...](https://quarkus.io/guides/smallrye-health)
