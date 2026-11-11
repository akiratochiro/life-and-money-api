-- Parâmetros tributários com vigência (Fase 4). São dados de referência, iguais para todos: não têm
-- user_id e não entram na exclusão de conta. Cada linha guarda a fonte legal ao lado do valor.
-- Um valor novo (o salário mínimo de 2027, por exemplo) é uma linha nova com outro valid_from, numa
-- migration nova; uma linha que já valeu nunca é alterada.

CREATE TABLE tax_parameters (
    id         BIGSERIAL     PRIMARY KEY,
    key        VARCHAR(40)   NOT NULL,
    valid_from DATE          NOT NULL,
    value      NUMERIC(19,6) NOT NULL,
    source     VARCHAR(500)  NOT NULL,
    CONSTRAINT uk_tax_parameters UNIQUE (key, valid_from),
    CONSTRAINT ck_tax_parameters_key CHECK (key IN (
        'MINIMUM_WAGE', 'INSS_CEILING', 'INSS_PRO_LABORE_RATE', 'IRRF_SIMPLIFIED_DISCOUNT', 'IRRF_DEPENDENT_DEDUCTION',
        'IRRF_REDUCTION_FULL_LIMIT', 'IRRF_REDUCTION_FULL_MAX', 'IRRF_REDUCTION_PARTIAL_LIMIT', 'IRRF_REDUCTION_A',
        'IRRF_REDUCTION_B', 'FATOR_R_THRESHOLD', 'DAS_MEI_INSS_RATE', 'DAS_MEI_ICMS', 'DAS_MEI_ISS', 'MEI_ANNUAL_LIMIT',
        'MEI_MONTHLY_LIMIT', 'MEI_TOLERANCE', 'SIMPLES_EPP_THRESHOLD', 'SIMPLES_SUBLIMIT', 'SIMPLES_CEILING',
        'SIMPLES_TOLERANCE', 'SIMPLES_ISS_CAP', 'DIVIDENDS_MONTHLY_EXEMPTION', 'DIVIDENDS_IRRF_RATE'))
);

-- Faixas dos Anexos III e V do Simples, com a repartição dos tributos (as frações somam 1).
CREATE TABLE simples_brackets (
    id            BIGSERIAL     PRIMARY KEY,
    annex         VARCHAR(5)    NOT NULL,
    bracket       INTEGER       NOT NULL,
    valid_from    DATE          NOT NULL,
    revenue_up_to NUMERIC(19,2) NOT NULL,
    nominal_rate  NUMERIC(7,6)  NOT NULL,
    deduction     NUMERIC(19,2) NOT NULL,
    irpj_share    NUMERIC(7,6)  NOT NULL,
    csll_share    NUMERIC(7,6)  NOT NULL,
    cofins_share  NUMERIC(7,6)  NOT NULL,
    pis_share     NUMERIC(7,6)  NOT NULL,
    cpp_share     NUMERIC(7,6)  NOT NULL,
    iss_share     NUMERIC(7,6)  NOT NULL,
    source        VARCHAR(500)  NOT NULL,
    CONSTRAINT uk_simples_brackets UNIQUE (annex, bracket, valid_from),
    CONSTRAINT ck_simples_brackets_annex CHECK (annex IN ('III', 'V')),
    CONSTRAINT ck_simples_brackets_bracket CHECK (bracket BETWEEN 1 AND 6),
    CONSTRAINT ck_simples_brackets_shares
        CHECK (irpj_share + csll_share + cofins_share + pis_share + cpp_share + iss_share = 1)
);

-- Tabela progressiva mensal do IRRF. income_up_to nulo é a última faixa, sem teto.
CREATE TABLE irrf_brackets (
    id           BIGSERIAL     PRIMARY KEY,
    valid_from   DATE          NOT NULL,
    bracket      INTEGER       NOT NULL,
    income_up_to NUMERIC(19,2),
    rate         NUMERIC(7,6)  NOT NULL,
    deduction    NUMERIC(19,2) NOT NULL,
    source       VARCHAR(500)  NOT NULL,
    CONSTRAINT uk_irrf_brackets UNIQUE (valid_from, bracket)
);
