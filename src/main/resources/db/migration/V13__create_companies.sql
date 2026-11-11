-- Minha Empresa (decisão 17). A empresa (companies) é a identidade; o enquadramento
-- (company_regimes) muda com o tempo e por isso tem vigência, como o orçamento: mudar fecha a
-- vigência atual e abre outra, e os meses passados continuam com o enquadramento da época.

CREATE TABLE companies (
    id                   BIGSERIAL     PRIMARY KEY,
    user_id              BIGINT        NOT NULL,
    name                 VARCHAR(100)  NOT NULL,
    emoji                VARCHAR(32)   NOT NULL,
    -- Só letras maiúsculas e dígitos: desde julho de 2026 o CNPJ pode ser alfanumérico.
    cnpj                 CHAR(14),
    opening_date         DATE          NOT NULL,
    -- O schema já aceita o regime de caixa; a API, por enquanto, só competência.
    revenue_basis        VARCHAR(20)   NOT NULL DEFAULT 'COMPETENCIA',
    -- Meta do usuário para alertas e para a calculadora. Quem decide o Anexo é o limite legal,
    -- que fica nos parâmetros tributários.
    fator_r_target       NUMERIC(5,4)  NOT NULL DEFAULT 0.28,
    -- Saldo da conta PJ no dia em que o usuário começa a acompanhar. Pode ser negativo.
    opening_balance      NUMERIC(19,2) NOT NULL DEFAULT 0,
    opening_balance_date DATE          NOT NULL,
    archived             BOOLEAN       NOT NULL DEFAULT false,
    archived_at          TIMESTAMPTZ,
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT fk_companies_user
        FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_companies_cnpj_format
        CHECK (cnpj ~ '^[0-9A-Z]{12}[0-9]{2}$'),
    CONSTRAINT ck_companies_revenue_basis
        CHECK (revenue_basis IN ('COMPETENCIA', 'CAIXA')),
    CONSTRAINT ck_companies_fator_r_target
        CHECK (fator_r_target > 0 AND fator_r_target <= 1),
    CONSTRAINT ck_companies_opening_balance_date
        CHECK (opening_balance_date >= opening_date),
    CONSTRAINT ck_companies_archived_at
        CHECK (archived = (archived_at IS NOT NULL))
);

CREATE INDEX idx_companies_user_id
    ON companies (user_id);

-- O mesmo CNPJ não entra duas vezes para o mesmo usuário, nem arquivado: desarquivar não pode
-- criar uma duplicata.
CREATE UNIQUE INDEX uk_companies_user_cnpj
    ON companies (user_id, cnpj) WHERE cnpj IS NOT NULL;

CREATE TABLE company_regimes (
    id           BIGSERIAL    PRIMARY KEY,
    user_id      BIGINT       NOT NULL,
    company_id   BIGINT       NOT NULL,
    legal_nature VARCHAR(20)  NOT NULL,
    tax_regime   VARCHAR(20)  NOT NULL,
    annex_config VARCHAR(20),
    mei_activity VARCHAR(20),
    valid_from   DATE         NOT NULL,
    valid_to     DATE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_company_regimes_user
        FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_company_regimes_company
        FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT ck_company_regimes_legal_nature
        CHECK (legal_nature IN ('MEI', 'EI', 'SLU', 'LTDA', 'SOCIEDADE_SIMPLES')),
    CONSTRAINT ck_company_regimes_tax_regime
        CHECK (tax_regime IN ('SIMEI', 'SIMPLES_NACIONAL', 'LUCRO_PRESUMIDO', 'LUCRO_REAL')),
    CONSTRAINT ck_company_regimes_annex_config
        CHECK (annex_config IN ('ANEXO_III_FIXO', 'FATOR_R')),
    CONSTRAINT ck_company_regimes_mei_activity
        CHECK (mei_activity IN ('SERVICOS', 'COMERCIO_INDUSTRIA', 'AMBOS')),
    -- As mesmas regras do RegimeRules, como última barreira: MEI se e somente se SIMEI, anexo
    -- se e somente se Simples Nacional, atividade se e somente se MEI.
    CONSTRAINT ck_company_regimes_mei_simei
        CHECK ((legal_nature = 'MEI') = (tax_regime = 'SIMEI')),
    CONSTRAINT ck_company_regimes_annex_only_simples
        CHECK ((tax_regime = 'SIMPLES_NACIONAL') = (annex_config IS NOT NULL)),
    CONSTRAINT ck_company_regimes_activity_only_mei
        CHECK ((legal_nature = 'MEI') = (mei_activity IS NOT NULL)),
    CONSTRAINT ck_company_regimes_valid_from_first_day
        CHECK (EXTRACT(DAY FROM valid_from) = 1),
    CONSTRAINT ck_company_regimes_valid_to_first_day
        CHECK (EXTRACT(DAY FROM valid_to) = 1),
    CONSTRAINT ck_company_regimes_valid_period
        CHECK (valid_to >= valid_from)
);

CREATE INDEX idx_company_regimes_user_id
    ON company_regimes (user_id);

CREATE INDEX idx_company_regimes_company_id
    ON company_regimes (company_id);

CREATE UNIQUE INDEX uk_company_regimes_active
    ON company_regimes (company_id) WHERE valid_to IS NULL;

-- A API aceita vários sócios (a folha do Fator R soma todos); a tela, por enquanto, mostra só um.
CREATE TABLE partners (
    id         BIGSERIAL    PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    company_id BIGINT       NOT NULL,
    name       VARCHAR(100) NOT NULL,
    is_me      BOOLEAN      NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_partners_user
        FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_partners_company
        FOREIGN KEY (company_id) REFERENCES companies (id)
);

CREATE INDEX idx_partners_user_id
    ON partners (user_id);

CREATE INDEX idx_partners_company_id
    ON partners (company_id);

-- Um só "sou eu" por empresa.
CREATE UNIQUE INDEX uk_partners_me
    ON partners (company_id) WHERE is_me;
