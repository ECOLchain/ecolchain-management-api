-- Carteira blockchain da empresa + revisão por item em valores DATA.

ALTER TABLE company ADD (wallet VARCHAR2(120));

ALTER TABLE company_attribute_value ADD (review_status VARCHAR2(30) DEFAULT 'PENDING' NOT NULL);
ALTER TABLE company_attribute_value ADD (review_note   VARCHAR2(2000));
ALTER TABLE company_attribute_value ADD CONSTRAINT ck_cav_review_status
    CHECK (review_status IN ('PENDING','ACCEPTED','REJECTED'));
