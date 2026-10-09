-- Empresa, membros, perfis escolhidos, valores de atributos e auditoria.

CREATE TABLE company (
    id               VARCHAR2(36)  NOT NULL PRIMARY KEY,
    cnpj             VARCHAR2(14)  NOT NULL,
    razao_social     VARCHAR2(300) NOT NULL,
    status           VARCHAR2(30)  NOT NULL,
    review_notes     VARCHAR2(2000),
    terms_version    VARCHAR2(40),
    terms_accepted_at TIMESTAMP,
    created_at       TIMESTAMP     NOT NULL,
    updated_at       TIMESTAMP     NOT NULL,
    submitted_at     TIMESTAMP,
    CONSTRAINT ck_company_status CHECK (status IN
        ('PENDING_DOCUMENTS','UNDER_REVIEW','CHANGES_REQUESTED','APPROVED','REJECTED','SUSPENDED','PENDING_UPDATE'))
);
CREATE UNIQUE INDEX uq_company_cnpj_active ON company (
    CASE WHEN status <> 'REJECTED' THEN cnpj END);
CREATE INDEX ix_company_status ON company (status);

CREATE TABLE company_member (
    id         VARCHAR2(36) NOT NULL PRIMARY KEY,
    company_id VARCHAR2(36) NOT NULL REFERENCES company (id),
    account_id VARCHAR2(36) NOT NULL REFERENCES account (id),
    role       VARCHAR2(30) NOT NULL,
    created_at TIMESTAMP    NOT NULL,
    CONSTRAINT uq_member UNIQUE (company_id, account_id),
    CONSTRAINT ck_member_role CHECK (role IN ('OWNER'))
);

CREATE TABLE company_profile (
    company_id      VARCHAR2(36) NOT NULL REFERENCES company (id),
    profile_type_id VARCHAR2(36) NOT NULL REFERENCES profile_type (id),
    CONSTRAINT pk_company_profile PRIMARY KEY (company_id, profile_type_id)
);

CREATE TABLE company_attribute_value (
    id           VARCHAR2(36) NOT NULL PRIMARY KEY,
    company_id   VARCHAR2(36) NOT NULL REFERENCES company (id),
    attribute_id VARCHAR2(36) NOT NULL REFERENCES attribute_definition (id),
    value_text   VARCHAR2(4000),
    updated_at   TIMESTAMP    NOT NULL,
    CONSTRAINT uq_cav UNIQUE (company_id, attribute_id)
);

CREATE TABLE audit_log (
    id          VARCHAR2(36) NOT NULL PRIMARY KEY,
    actor_id    VARCHAR2(36),
    actor_email VARCHAR2(320),
    action      VARCHAR2(80) NOT NULL,
    entity_type VARCHAR2(60),
    entity_id   VARCHAR2(36),
    detail      VARCHAR2(4000),
    created_at  TIMESTAMP    NOT NULL
);
CREATE INDEX ix_audit_entity ON audit_log (entity_type, entity_id);
