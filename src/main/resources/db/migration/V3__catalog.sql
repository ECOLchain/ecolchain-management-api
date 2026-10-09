-- Catalogo dinamico: tipos de perfil, passos, atributos e ligacao passo->atributo
-- + seed completo do Anexo D da proposta.

CREATE TABLE profile_type (
    id           VARCHAR2(36)  NOT NULL PRIMARY KEY,
    code         VARCHAR2(60)  NOT NULL,
    name_pt      VARCHAR2(200) NOT NULL,
    name_en      VARCHAR2(200) NOT NULL,
    desc_pt      VARCHAR2(1000),
    desc_en      VARCHAR2(1000),
    active       NUMBER(1)     NOT NULL DEFAULT 1,
    position     NUMBER(5)     NOT NULL DEFAULT 0,
    created_at   TIMESTAMP     NOT NULL,
    updated_at   TIMESTAMP     NOT NULL,
    CONSTRAINT uq_profile_type_code UNIQUE (code)
);

CREATE TABLE onboarding_step (
    id              VARCHAR2(36)  NOT NULL PRIMARY KEY,
    profile_type_id VARCHAR2(36)  NOT NULL REFERENCES profile_type (id),
    code            VARCHAR2(60)  NOT NULL,
    title_pt        VARCHAR2(300) NOT NULL,
    title_en        VARCHAR2(300) NOT NULL,
    position        NUMBER(5)     NOT NULL,
    CONSTRAINT uq_step_code UNIQUE (profile_type_id, code)
);

CREATE TABLE attribute_definition (
    id         VARCHAR2(36)   NOT NULL PRIMARY KEY,
    code       VARCHAR2(80)   NOT NULL,
    kind       VARCHAR2(10)   NOT NULL,
    label_pt   VARCHAR2(300)  NOT NULL,
    label_en   VARCHAR2(300)  NOT NULL,
    help_pt    VARCHAR2(1000),
    help_en    VARCHAR2(1000),
    rules      VARCHAR2(4000),
    active     NUMBER(1)      NOT NULL DEFAULT 1,
    created_at TIMESTAMP      NOT NULL,
    updated_at TIMESTAMP      NOT NULL,
    CONSTRAINT uq_attribute_code UNIQUE (code),
    CONSTRAINT ck_attribute_kind CHECK (kind IN ('DATA', 'DOCUMENT')),
    CONSTRAINT ck_attribute_rules_json CHECK (rules IS NULL OR rules IS JSON)
);

CREATE TABLE step_attribute (
    step_id      VARCHAR2(36) NOT NULL REFERENCES onboarding_step (id),
    attribute_id VARCHAR2(36) NOT NULL REFERENCES attribute_definition (id),
    required     NUMBER(1)    NOT NULL DEFAULT 0,
    position     NUMBER(5)    NOT NULL,
    CONSTRAINT pk_step_attribute PRIMARY KEY (step_id, attribute_id)
);

-- ============ SEED — Anexo D ============
-- Tipos de perfil (uuid deterministico p/ leitura do seed)
INSERT INTO profile_type (id, code, name_pt, name_en, desc_pt, desc_en, active, position, created_at, updated_at) VALUES
 ('b1000000-0000-4000-8000-000000000001','GERADOR','Gerador de resíduos','Waste generator','Importadora / cliente solicitante','Importer / requesting customer',1,10,SYSTIMESTAMP,SYSTIMESTAMP);
INSERT INTO profile_type (id, code, name_pt, name_en, desc_pt, desc_en, active, position, created_at, updated_at) VALUES
 ('b1000000-0000-4000-8000-000000000002','TRANSPORTADOR','Transportador','Transporter','Transporte de resíduos','Waste transportation',1,20,SYSTIMESTAMP,SYSTIMESTAMP);
INSERT INTO profile_type (id, code, name_pt, name_en, desc_pt, desc_en, active, position, created_at, updated_at) VALUES
 ('b1000000-0000-4000-8000-000000000003','COOPERATIVA','Cooperativa','Cooperative','Cooperativa de catadores','Waste pickers cooperative',1,30,SYSTIMESTAMP,SYSTIMESTAMP);
INSERT INTO profile_type (id, code, name_pt, name_en, desc_pt, desc_en, active, position, created_at, updated_at) VALUES
 ('b1000000-0000-4000-8000-000000000004','CLEANTECH','Cleantech','Cleantech','Startup de tecnologia limpa','Clean technology startup',1,40,SYSTIMESTAMP,SYSTIMESTAMP);
INSERT INTO profile_type (id, code, name_pt, name_en, desc_pt, desc_en, active, position, created_at, updated_at) VALUES
 ('b1000000-0000-4000-8000-000000000005','RECICLADORA','Recicladora','Recycler','Destinador final / reciclagem','Final destination / recycling',1,50,SYSTIMESTAMP,SYSTIMESTAMP);
INSERT INTO profile_type (id, code, name_pt, name_en, desc_pt, desc_en, active, position, created_at, updated_at) VALUES
 ('b1000000-0000-4000-8000-000000000006','HIGIENIZADORA','Higienizadora','Sanitizer','Reuso de garrafas / higienização','Bottle reuse / sanitization',1,60,SYSTIMESTAMP,SYSTIMESTAMP);

-- Atributos compartilhados + especificos (code -> uuid determinístico)
INSERT INTO attribute_definition (id, code, kind, label_pt, label_en, help_pt, help_en, rules, active, created_at, updated_at) VALUES
 ('c1000000-0000-4000-8000-000000000001','full_address','DATA','Endereço completo','Full address',NULL,NULL,'{"input":"text","maxLength":300}',1,SYSTIMESTAMP,SYSTIMESTAMP);
INSERT INTO attribute_definition (id, code, kind, label_pt, label_en, help_pt, help_en, rules, active, created_at, updated_at) VALUES
 ('c1000000-0000-4000-8000-000000000002','state_registration','DATA','Inscrição Estadual (IE)','State registration',NULL,NULL,'{"input":"text","maxLength":30}',1,SYSTIMESTAMP,SYSTIMESTAMP);
INSERT INTO attribute_definition (id, code, kind, label_pt, label_en, help_pt, help_en, rules, active, created_at, updated_at) VALUES
 ('c1000000-0000-4000-8000-000000000003','municipal_registration','DATA','Inscrição Municipal (IM)','Municipal registration',NULL,NULL,'{"input":"text","maxLength":30}',1,SYSTIMESTAMP,SYSTIMESTAMP);
INSERT INTO attribute_definition (id, code, kind, label_pt, label_en, help_pt, help_en, rules, active, created_at, updated_at) VALUES
 ('c1000000-0000-4000-8000-000000000004','primary_cnae','DATA','CNAE principal','Primary CNAE',NULL,NULL,'{"input":"text","maxLength":20}',1,SYSTIMESTAMP,SYSTIMESTAMP);
INSERT INTO attribute_definition (id, code, kind, label_pt, label_en, help_pt, help_en, rules, active, created_at, updated_at) VALUES
 ('c1000000-0000-4000-8000-000000000005','secondary_cnaes','DATA','CNAEs secundários','Secondary CNAEs','Separados por vírgula','Comma-separated',NULL,1,SYSTIMESTAMP,SYSTIMESTAMP);
INSERT INTO attribute_definition (id, code, kind, label_pt, label_en, help_pt, help_en, rules, active, created_at, updated_at) VALUES
 ('c1000000-0000-4000-8000-000000000006','sinir_number','DATA','Registro SINIR / MTR Nacional','SINIR / National MTR registry',NULL,NULL,'{"input":"text","maxLength":40}',1,SYSTIMESTAMP,SYSTIMESTAMP);
INSERT INTO attribute_definition (id, code, kind, label_pt, label_en, help_pt, help_en, rules, active, created_at, updated_at) VALUES
 ('c1000000-0000-4000-8000-000000000007','sigor_number','DATA','Registro SIGOR / MTR SP (CETESB)','SIGOR / MTR SP registry (CETESB)','Apenas São Paulo','São Paulo only',NULL,1,SYSTIMESTAMP,SYSTIMESTAMP);
INSERT INTO attribute_definition (id, code, kind, label_pt, label_en, help_pt, help_en, rules, active, created_at, updated_at) VALUES
 ('c1000000-0000-4000-8000-000000000008','responsible_cpf','DATA','CPF do responsável legal','Legal representative CPF',NULL,NULL,'{"input":"text","maxLength":14,"regex":"^[0-9]{11}$"}',1,SYSTIMESTAMP,SYSTIMESTAMP);
INSERT INTO attribute_definition (id, code, kind, label_pt, label_en, help_pt, help_en, rules, active, created_at, updated_at) VALUES
 ('c1000000-0000-4000-8000-000000000009','corporate_email','DATA','E-mail corporativo','Corporate e-mail',NULL,NULL,'{"input":"text","maxLength":320}',1,SYSTIMESTAMP,SYSTIMESTAMP);
INSERT INTO attribute_definition (id, code, kind, label_pt, label_en, help_pt, help_en, rules, active, created_at, updated_at) VALUES
 ('c1000000-0000-4000-8000-00000000000a','phone','DATA','Telefone','Phone',NULL,NULL,'{"input":"text","maxLength":30}',1,SYSTIMESTAMP,SYSTIMESTAMP);
INSERT INTO attribute_definition (id, code, kind, label_pt, label_en, help_pt, help_en, rules, active, created_at, updated_at) VALUES
 ('c1000000-0000-4000-8000-000000000010','incorporation_minutes','DOCUMENT','Ata da Assembleia Geral de Constituição','Incorporation general assembly minutes','PDF, JPG ou PNG','PDF, JPG or PNG','{"extensoesPermitidas":["pdf","jpg","png"]}',1,SYSTIMESTAMP,SYSTIMESTAMP);
INSERT INTO attribute_definition (id, code, kind, label_pt, label_en, help_pt, help_en, rules, active, created_at, updated_at) VALUES
 ('c1000000-0000-4000-8000-000000000011','bylaws','DOCUMENT','Estatuto Social','Bylaws','PDF, JPG ou PNG','PDF, JPG or PNG','{"extensoesPermitidas":["pdf","jpg","png"]}',1,SYSTIMESTAMP,SYSTIMESTAMP);
INSERT INTO attribute_definition (id, code, kind, label_pt, label_en, help_pt, help_en, rules, active, created_at, updated_at) VALUES
 ('c1000000-0000-4000-8000-000000000012','founding_members_list','DOCUMENT','Lista de Associados Fundadores','Founding members list','PDF, JPG ou PNG','PDF, JPG or PNG','{"extensoesPermitidas":["pdf","jpg","png"]}',1,SYSTIMESTAMP,SYSTIMESTAMP);
INSERT INTO attribute_definition (id, code, kind, label_pt, label_en, help_pt, help_en, rules, active, created_at, updated_at) VALUES
 ('c1000000-0000-4000-8000-000000000013','board_member_rg','DOCUMENT','RG da diretoria','Board members ID (RG)','Todos em um único PDF, JPG ou PNG','All in a single PDF, JPG or PNG','{"extensoesPermitidas":["pdf","jpg","png"]}',1,SYSTIMESTAMP,SYSTIMESTAMP);
INSERT INTO attribute_definition (id, code, kind, label_pt, label_en, help_pt, help_en, rules, active, created_at, updated_at) VALUES
 ('c1000000-0000-4000-8000-000000000014','board_member_cpf','DOCUMENT','CPF da diretoria','Board members CPF','Todos em um único PDF, JPG ou PNG','All in a single PDF, JPG or PNG','{"extensoesPermitidas":["pdf","jpg","png"]}',1,SYSTIMESTAMP,SYSTIMESTAMP);
INSERT INTO attribute_definition (id, code, kind, label_pt, label_en, help_pt, help_en, rules, active, created_at, updated_at) VALUES
 ('c1000000-0000-4000-8000-000000000015','board_member_address_proof','DOCUMENT','Comprovante de residência da diretoria','Board members address proof','PDF, JPG ou PNG','PDF, JPG or PNG','{"extensoesPermitidas":["pdf","jpg","png"]}',1,SYSTIMESTAMP,SYSTIMESTAMP);
INSERT INTO attribute_definition (id, code, kind, label_pt, label_en, help_pt, help_en, rules, active, created_at, updated_at) VALUES
 ('c1000000-0000-4000-8000-000000000016','catadores_sinir_registry','DATA','Habilitação no Módulo Catadores do SINIR','SINIR Waste Pickers Module registration',NULL,NULL,'{"input":"text","maxLength":60}',1,SYSTIMESTAMP,SYSTIMESTAMP);
INSERT INTO attribute_definition (id, code, kind, label_pt, label_en, help_pt, help_en, rules, active, created_at, updated_at) VALUES
 ('c1000000-0000-4000-8000-000000000017','infrastructure_declaration','DOCUMENT','Comprovação de galpão/equipamentos de triagem','Warehouse and sorting equipment proof','PDF, JPG ou PNG','PDF, JPG or PNG','{"extensoesPermitidas":["pdf","jpg","png"]}',1,SYSTIMESTAMP,SYSTIMESTAMP);
INSERT INTO attribute_definition (id, code, kind, label_pt, label_en, help_pt, help_en, rules, active, created_at, updated_at) VALUES
 ('c1000000-0000-4000-8000-000000000018','articles_of_association','DOCUMENT','Contrato Social','Articles of association','LTDA / Marco Legal das Startups — PDF, JPG ou PNG','PDF, JPG or PNG','{"extensoesPermitidas":["pdf","jpg","png"]}',1,SYSTIMESTAMP,SYSTIMESTAMP);
INSERT INTO attribute_definition (id, code, kind, label_pt, label_en, help_pt, help_en, rules, active, created_at, updated_at) VALUES
 ('c1000000-0000-4000-8000-000000000019','partner_rg','DOCUMENT','RG dos sócios','Partners ID (RG)','Todos em um único PDF, JPG ou PNG','All in a single PDF, JPG or PNG','{"extensoesPermitidas":["pdf","jpg","png"]}',1,SYSTIMESTAMP,SYSTIMESTAMP);
INSERT INTO attribute_definition (id, code, kind, label_pt, label_en, help_pt, help_en, rules, active, created_at, updated_at) VALUES
 ('c1000000-0000-4000-8000-00000000001a','partner_cpf','DOCUMENT','CPF dos sócios','Partners CPF','Todos em um único PDF, JPG ou PNG','All in a single PDF, JPG or PNG','{"extensoesPermitidas":["pdf","jpg","png"]}',1,SYSTIMESTAMP,SYSTIMESTAMP);
INSERT INTO attribute_definition (id, code, kind, label_pt, label_en, help_pt, help_en, rules, active, created_at, updated_at) VALUES
 ('c1000000-0000-4000-8000-00000000001b','business_permit','DOCUMENT','Alvará de Funcionamento','Business permit','PDF, JPG ou PNG','PDF, JPG or PNG','{"extensoesPermitidas":["pdf","jpg","png"]}',1,SYSTIMESTAMP,SYSTIMESTAMP);
INSERT INTO attribute_definition (id, code, kind, label_pt, label_en, help_pt, help_en, rules, active, created_at, updated_at) VALUES
 ('c1000000-0000-4000-8000-00000000001c','env_operating_license','DOCUMENT','Licença Ambiental de Operação (LAO)','Environmental Operating License','LAO ou dispensa — PDF, JPG ou PNG','LAO or waiver — PDF, JPG or PNG','{"extensoesPermitidas":["pdf","jpg","png"]}',1,SYSTIMESTAMP,SYSTIMESTAMP);
INSERT INTO attribute_definition (id, code, kind, label_pt, label_en, help_pt, help_en, rules, active, created_at, updated_at) VALUES
 ('c1000000-0000-4000-8000-00000000001d','sinir_api_registration','DATA','Cadastro SINIR (contratada/usuário API)','SINIR registration (contractor/API user)',NULL,NULL,'{"input":"text","maxLength":60}',1,SYSTIMESTAMP,SYSTIMESTAMP);
INSERT INTO attribute_definition (id, code, kind, label_pt, label_en, help_pt, help_en, rules, active, created_at, updated_at) VALUES
 ('c1000000-0000-4000-8000-00000000001e','env_license_number','DATA','Número da LAO','LAO number','Obrigatório para recebimento no MTR','Required for MTR intake','{"input":"text","maxLength":60}',1,SYSTIMESTAMP,SYSTIMESTAMP);
INSERT INTO attribute_definition (id, code, kind, label_pt, label_en, help_pt, help_en, rules, active, created_at, updated_at) VALUES
 ('c1000000-0000-4000-8000-00000000001f','env_license_expiry','DATA','Validade da LAO','LAO expiry date',NULL,NULL,'{"input":"text","maxLength":20}',1,SYSTIMESTAMP,SYSTIMESTAMP);
INSERT INTO attribute_definition (id, code, kind, label_pt, label_en, help_pt, help_en, rules, active, created_at, updated_at) VALUES
 ('c1000000-0000-4000-8000-000000000020','cdf_capability','DATA','Declaração de emissão de CDF vinculado a MTRs','CDF issuance declaration linked to MTRs',NULL,NULL,'{"input":"text","maxLength":300}',1,SYSTIMESTAMP,SYSTIMESTAMP);
INSERT INTO attribute_definition (id, code, kind, label_pt, label_en, help_pt, help_en, rules, active, created_at, updated_at) VALUES
 ('c1000000-0000-4000-8000-000000000021','inbound_invoice','DOCUMENT','NF de entrada (exemplar)','Inbound invoice (sample)','Créditos e logística reversa — PDF, JPG ou PNG','PDF, JPG or PNG','{"extensoesPermitidas":["pdf","jpg","png"]}',1,SYSTIMESTAMP,SYSTIMESTAMP);
INSERT INTO attribute_definition (id, code, kind, label_pt, label_en, help_pt, help_en, rules, active, created_at, updated_at) VALUES
 ('c1000000-0000-4000-8000-000000000022','env_license_hygiene','DOCUMENT','LAO com autorização de lavagem/reuso','LAO with washing/reuse authorization','Autorização explícita de higienização — PDF, JPG ou PNG','Explicit sanitization authorization — PDF, JPG or PNG','{"extensoesPermitidas":["pdf","jpg","png"]}',1,SYSTIMESTAMP,SYSTIMESTAMP);
INSERT INTO attribute_definition (id, code, kind, label_pt, label_en, help_pt, help_en, rules, active, created_at, updated_at) VALUES
 ('c1000000-0000-4000-8000-000000000023','health_permit','DOCUMENT','CMVS / Alvará Sanitário','Health permit (CMVS)','PDF, JPG ou PNG','PDF, JPG or PNG','{"extensoesPermitidas":["pdf","jpg","png"]}',1,SYSTIMESTAMP,SYSTIMESTAMP);
INSERT INTO attribute_definition (id, code, kind, label_pt, label_en, help_pt, help_en, rules, active, created_at, updated_at) VALUES
 ('c1000000-0000-4000-8000-000000000024','state_registration_proof','DOCUMENT','Comprovante de Inscrição Estadual','State registration proof','PDF, JPG ou PNG','PDF, JPG or PNG','{"extensoesPermitidas":["pdf","jpg","png"]}',1,SYSTIMESTAMP,SYSTIMESTAMP);

-- Passos GERADOR: dados_empresa, cadastros, responsavel
INSERT INTO onboarding_step (id, profile_type_id, code, title_pt, title_en, position) VALUES
 ('d1000000-0000-4000-8000-000000000001','b1000000-0000-4000-8000-000000000001','dados_empresa','Dados da empresa','Company data',10);
INSERT INTO onboarding_step (id, profile_type_id, code, title_pt, title_en, position) VALUES
 ('d1000000-0000-4000-8000-000000000002','b1000000-0000-4000-8000-000000000001','cadastros','Cadastros ambientais','Environmental registries',20);
INSERT INTO onboarding_step (id, profile_type_id, code, title_pt, title_en, position) VALUES
 ('d1000000-0000-4000-8000-000000000003','b1000000-0000-4000-8000-000000000001','responsavel','Responsável legal','Legal representative',30);
INSERT INTO step_attribute (step_id, attribute_id, required, position) VALUES
 ('d1000000-0000-4000-8000-000000000001','c1000000-0000-4000-8000-000000000001',1,10),
 ('d1000000-0000-4000-8000-000000000001','c1000000-0000-4000-8000-000000000002',1,20),
 ('d1000000-0000-4000-8000-000000000001','c1000000-0000-4000-8000-000000000003',0,30),
 ('d1000000-0000-4000-8000-000000000001','c1000000-0000-4000-8000-000000000004',1,40),
 ('d1000000-0000-4000-8000-000000000001','c1000000-0000-4000-8000-000000000005',0,50),
 ('d1000000-0000-4000-8000-000000000002','c1000000-0000-4000-8000-000000000006',1,10),
 ('d1000000-0000-4000-8000-000000000002','c1000000-0000-4000-8000-000000000007',0,20),
 ('d1000000-0000-4000-8000-000000000003','c1000000-0000-4000-8000-000000000008',1,10),
 ('d1000000-0000-4000-8000-000000000003','c1000000-0000-4000-8000-000000000009',0,20),
 ('d1000000-0000-4000-8000-000000000003','c1000000-0000-4000-8000-00000000000a',1,30);

-- Passo TRANSPORTADOR: cadastros
INSERT INTO onboarding_step (id, profile_type_id, code, title_pt, title_en, position) VALUES
 ('d1000000-0000-4000-8000-000000000004','b1000000-0000-4000-8000-000000000002','cadastros','Cadastros ambientais','Environmental registries',10);
INSERT INTO step_attribute (step_id, attribute_id, required, position) VALUES
 ('d1000000-0000-4000-8000-000000000004','c1000000-0000-4000-8000-000000000006',1,10),
 ('d1000000-0000-4000-8000-000000000004','c1000000-0000-4000-8000-000000000007',0,20);

-- Passos COOPERATIVA: abertura_legal, cadastros_operacao, responsavel
INSERT INTO onboarding_step (id, profile_type_id, code, title_pt, title_en, position) VALUES
 ('d1000000-0000-4000-8000-000000000005','b1000000-0000-4000-8000-000000000003','abertura_legal','Abertura legal','Legal incorporation',10);
INSERT INTO onboarding_step (id, profile_type_id, code, title_pt, title_en, position) VALUES
 ('d1000000-0000-4000-8000-000000000006','b1000000-0000-4000-8000-000000000003','cadastros_operacao','Cadastros e operação','Registries and operations',20);
INSERT INTO onboarding_step (id, profile_type_id, code, title_pt, title_en, position) VALUES
 ('d1000000-0000-4000-8000-000000000007','b1000000-0000-4000-8000-000000000003','responsavel','Responsável legal','Legal representative',30);
INSERT INTO step_attribute (step_id, attribute_id, required, position) VALUES
 ('d1000000-0000-4000-8000-000000000005','c1000000-0000-4000-8000-000000000010',1,10),
 ('d1000000-0000-4000-8000-000000000005','c1000000-0000-4000-8000-000000000011',1,20),
 ('d1000000-0000-4000-8000-000000000005','c1000000-0000-4000-8000-000000000012',1,30),
 ('d1000000-0000-4000-8000-000000000007','c1000000-0000-4000-8000-000000000013',1,10),
 ('d1000000-0000-4000-8000-000000000007','c1000000-0000-4000-8000-000000000014',1,20),
 ('d1000000-0000-4000-8000-000000000007','c1000000-0000-4000-8000-000000000015',1,30),
 ('d1000000-0000-4000-8000-000000000007','c1000000-0000-4000-8000-000000000008',1,40),
 ('d1000000-0000-4000-8000-000000000006','c1000000-0000-4000-8000-000000000016',1,10),
 ('d1000000-0000-4000-8000-000000000006','c1000000-0000-4000-8000-000000000017',1,20);

-- Passos CLEANTECH: abertura_legal, cadastros, responsavel
INSERT INTO onboarding_step (id, profile_type_id, code, title_pt, title_en, position) VALUES
 ('d1000000-0000-4000-8000-000000000008','b1000000-0000-4000-8000-000000000004','abertura_legal','Abertura legal','Legal incorporation',10);
INSERT INTO onboarding_step (id, profile_type_id, code, title_pt, title_en, position) VALUES
 ('d1000000-0000-4000-8000-000000000009','b1000000-0000-4000-8000-000000000004','cadastros','Cadastros ambientais','Environmental registries',20);
INSERT INTO onboarding_step (id, profile_type_id, code, title_pt, title_en, position) VALUES
 ('d1000000-0000-4000-8000-00000000000a','b1000000-0000-4000-8000-000000000004','responsavel','Responsável legal','Legal representative',30);
INSERT INTO step_attribute (step_id, attribute_id, required, position) VALUES
 ('d1000000-0000-4000-8000-000000000008','c1000000-0000-4000-8000-000000000018',1,10),
 ('d1000000-0000-4000-8000-000000000008','c1000000-0000-4000-8000-00000000001b',1,20),
 ('d1000000-0000-4000-8000-000000000009','c1000000-0000-4000-8000-000000000004',1,10),
 ('d1000000-0000-4000-8000-000000000009','c1000000-0000-4000-8000-000000000005',0,20),
 ('d1000000-0000-4000-8000-000000000009','c1000000-0000-4000-8000-000000000002',0,30),
 ('d1000000-0000-4000-8000-000000000009','c1000000-0000-4000-8000-00000000001c',0,40),
 ('d1000000-0000-4000-8000-000000000009','c1000000-0000-4000-8000-00000000001d',0,50),
 ('d1000000-0000-4000-8000-00000000000a','c1000000-0000-4000-8000-000000000019',1,10),
 ('d1000000-0000-4000-8000-00000000000a','c1000000-0000-4000-8000-00000000001a',1,20),
 ('d1000000-0000-4000-8000-00000000000a','c1000000-0000-4000-8000-000000000008',1,30);

-- Passos RECICLADORA: licencas, fiscal_operacao
INSERT INTO onboarding_step (id, profile_type_id, code, title_pt, title_en, position) VALUES
 ('d1000000-0000-4000-8000-00000000000b','b1000000-0000-4000-8000-000000000005','licencas','Licenças e autorizações','Licenses and authorizations',10);
INSERT INTO onboarding_step (id, profile_type_id, code, title_pt, title_en, position) VALUES
 ('d1000000-0000-4000-8000-00000000000c','b1000000-0000-4000-8000-000000000005','fiscal_operacao','Fiscal e operação','Tax and operations',20);
INSERT INTO step_attribute (step_id, attribute_id, required, position) VALUES
 ('d1000000-0000-4000-8000-00000000000b','c1000000-0000-4000-8000-00000000001c',1,10),
 ('d1000000-0000-4000-8000-00000000000b','c1000000-0000-4000-8000-00000000001e',1,20),
 ('d1000000-0000-4000-8000-00000000000b','c1000000-0000-4000-8000-00000000001f',1,30),
 ('d1000000-0000-4000-8000-00000000000c','c1000000-0000-4000-8000-000000000020',1,10),
 ('d1000000-0000-4000-8000-00000000000c','c1000000-0000-4000-8000-000000000021',1,20),
 ('d1000000-0000-4000-8000-00000000000c','c1000000-0000-4000-8000-000000000004',1,30),
 ('d1000000-0000-4000-8000-00000000000c','c1000000-0000-4000-8000-000000000005',0,40);

-- Passos HIGIENIZADORA: licencas, fiscal
INSERT INTO onboarding_step (id, profile_type_id, code, title_pt, title_en, position) VALUES
 ('d1000000-0000-4000-8000-00000000000d','b1000000-0000-4000-8000-000000000006','licencas','Licenças e autorizações','Licenses and authorizations',10);
INSERT INTO onboarding_step (id, profile_type_id, code, title_pt, title_en, position) VALUES
 ('d1000000-0000-4000-8000-00000000000e','b1000000-0000-4000-8000-000000000006','fiscal','Fiscal','Tax',20);
INSERT INTO step_attribute (step_id, attribute_id, required, position) VALUES
 ('d1000000-0000-4000-8000-00000000000d','c1000000-0000-4000-8000-000000000022',1,10),
 ('d1000000-0000-4000-8000-00000000000d','c1000000-0000-4000-8000-000000000023',1,20),
 ('d1000000-0000-4000-8000-00000000000e','c1000000-0000-4000-8000-000000000002',1,10),
 ('d1000000-0000-4000-8000-00000000000e','c1000000-0000-4000-8000-000000000024',1,20);
