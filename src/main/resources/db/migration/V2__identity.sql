-- Identity: contas, OTP e refresh tokens (proposta, Anexo A).
-- UUIDs como VARCHAR2(36) texto. Timestamps em UTC.

CREATE TABLE account (
    id          VARCHAR2(36)  NOT NULL PRIMARY KEY,
    email       VARCHAR2(320) NOT NULL,
    full_name   VARCHAR2(300),
    role        VARCHAR2(30)  NOT NULL,
    status      VARCHAR2(30)  NOT NULL,
    created_at  TIMESTAMP     NOT NULL,
    updated_at  TIMESTAMP     NOT NULL,
    CONSTRAINT uq_account_email UNIQUE (email),
    CONSTRAINT ck_account_role CHECK (role IN ('COMPANY_OWNER', 'PLATFORM_ADMIN')),
    CONSTRAINT ck_account_status CHECK (status IN ('EMAIL_VERIFIED', 'ACTIVE', 'BLOCKED'))
);
CREATE INDEX ix_account_email ON account (email);

CREATE TABLE email_otp (
    id           VARCHAR2(36)  NOT NULL PRIMARY KEY,
    email        VARCHAR2(320) NOT NULL,
    code_hmac    VARCHAR2(128) NOT NULL,
    expires_at   TIMESTAMP     NOT NULL,
    attempts     NUMBER(3)     NOT NULL DEFAULT 0,
    consumed_at  TIMESTAMP,
    request_ip   VARCHAR2(45),
    created_at   TIMESTAMP     NOT NULL
);
CREATE INDEX ix_email_otp_email ON email_otp (email, created_at);

CREATE TABLE refresh_token (
    id                VARCHAR2(36)  NOT NULL PRIMARY KEY,
    account_id        VARCHAR2(36)  NOT NULL REFERENCES account (id),
    family_id         VARCHAR2(36)  NOT NULL,
    token_hash        VARCHAR2(128) NOT NULL,
    expires_at        TIMESTAMP     NOT NULL,
    family_expires_at TIMESTAMP     NOT NULL,
    revoked_at        TIMESTAMP,
    replaced_by       VARCHAR2(36),
    created_at        TIMESTAMP     NOT NULL
);
CREATE INDEX ix_refresh_token_family ON refresh_token (family_id);
CREATE INDEX ix_refresh_token_account ON refresh_token (account_id);
CREATE UNIQUE INDEX uq_refresh_token_hash ON refresh_token (token_hash);

-- Seed: admin da plataforma (login pelo mesmo OTP de todo mundo).
INSERT INTO account (id, email, full_name, role, status, created_at, updated_at)
VALUES ('a0000000-0000-4000-8000-000000000001', 'leandroluz201616@gmail.com',
        'Leandro Leite', 'PLATFORM_ADMIN', 'EMAIL_VERIFIED', SYSTIMESTAMP, SYSTIMESTAMP);
