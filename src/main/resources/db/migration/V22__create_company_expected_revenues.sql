-- O faturamento esperado do mês de uma empresa (Fase 8), útil para contrato fixo: a projeção do mês
-- usa o maior entre ele e as notas já lançadas. Tem vigência como o pró-labore planejado (decisão 2):
-- mudar vale a partir do mês corrente, e os meses passados guardam o valor da época.
CREATE TABLE company_expected_revenues (
    id          BIGSERIAL     PRIMARY KEY,
    user_id     BIGINT        NOT NULL,
    company_id  BIGINT        NOT NULL,
    amount      NUMERIC(19,2) NOT NULL,
    valid_from  DATE          NOT NULL,
    valid_to    DATE,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT fk_company_expected_revenues_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_company_expected_revenues_company FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT ck_company_expected_revenues_amount CHECK (amount > 0),
    CONSTRAINT ck_company_expected_revenues_from_first_day CHECK (EXTRACT(DAY FROM valid_from) = 1),
    CONSTRAINT ck_company_expected_revenues_to_first_day CHECK (EXTRACT(DAY FROM valid_to) = 1),
    CONSTRAINT ck_company_expected_revenues_period CHECK (valid_to >= valid_from)
);

CREATE INDEX idx_company_expected_revenues_user_id ON company_expected_revenues (user_id);
CREATE UNIQUE INDEX uk_company_expected_revenues_active ON company_expected_revenues (company_id) WHERE valid_to IS NULL;
