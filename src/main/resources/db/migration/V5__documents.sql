-- Documentos enviados (intenção de upload + confirmação + revisão).

CREATE TABLE document (
    id           VARCHAR2(36)  NOT NULL PRIMARY KEY,
    company_id   VARCHAR2(36)  NOT NULL REFERENCES company (id),
    attribute_id VARCHAR2(36)  NOT NULL REFERENCES attribute_definition (id),
    object_key   VARCHAR2(500) NOT NULL,
    file_name    VARCHAR2(300) NOT NULL,
    content_type VARCHAR2(120) NOT NULL,
    size_bytes   NUMBER(19)    NOT NULL,
    status       VARCHAR2(30)  NOT NULL,
    review_note  VARCHAR2(2000),
    uploaded_at  TIMESTAMP,
    created_at   TIMESTAMP     NOT NULL,
    CONSTRAINT uq_document_company_attr UNIQUE (company_id, attribute_id),
    CONSTRAINT uq_document_key UNIQUE (object_key),
    CONSTRAINT ck_document_status CHECK (status IN ('PENDING_UPLOAD','UPLOADED','REJECTED','ACCEPTED'))
);
CREATE INDEX ix_document_company ON document (company_id);
