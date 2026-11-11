-- Pró-labore (Fase 4) e retiradas (Fase 6) dos sócios. A tabela de retiradas já nasce completa: a
-- Fase 6 acrescenta a distribuição de lucros e a ponte com as finanças pessoais sem migration nova.

-- O pró-labore planejado de cada sócio, com vigência como o orçamento (decisão 2): é o que vale para
-- a previsão do mês corrente e dos próximos, e o que vem preenchido ao registrar o pró-labore pago.
CREATE TABLE partner_pro_labore_plans (
    id           BIGSERIAL     PRIMARY KEY,
    user_id      BIGINT        NOT NULL,
    partner_id   BIGINT        NOT NULL,
    gross_amount NUMERIC(19,2) NOT NULL,
    valid_from   DATE          NOT NULL,
    valid_to     DATE,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT fk_partner_pro_labore_plans_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_partner_pro_labore_plans_partner FOREIGN KEY (partner_id) REFERENCES partners (id),
    CONSTRAINT ck_partner_pro_labore_plans_amount CHECK (gross_amount > 0),
    CONSTRAINT ck_partner_pro_labore_plans_from_first_day CHECK (EXTRACT(DAY FROM valid_from) = 1),
    CONSTRAINT ck_partner_pro_labore_plans_to_first_day CHECK (EXTRACT(DAY FROM valid_to) = 1),
    CONSTRAINT ck_partner_pro_labore_plans_period CHECK (valid_to >= valid_from)
);

CREATE INDEX idx_partner_pro_labore_plans_user_id ON partner_pro_labore_plans (user_id);
CREATE UNIQUE INDEX uk_partner_pro_labore_plans_active ON partner_pro_labore_plans (partner_id) WHERE valid_to IS NULL;

-- O que saiu da conta da empresa para um sócio. Não é despesa: o resultado da empresa não muda, só
-- o caixa. INSS e IRRF ficam guardados como foram retidos: uma tabela nova de IR não reescreve o passado.
CREATE TABLE withdrawals (
    id                      BIGSERIAL     PRIMARY KEY,
    user_id                 BIGINT        NOT NULL,
    company_id              BIGINT        NOT NULL,
    partner_id              BIGINT        NOT NULL,
    kind                    VARCHAR(20)   NOT NULL,
    -- No pró-labore, o mês da folha (é por ele que o INSS e o Fator R contam).
    competence_month        DATE,
    payment_date            DATE          NOT NULL,
    gross_amount            NUMERIC(19,2) NOT NULL,
    inss_amount             NUMERIC(19,2) NOT NULL DEFAULT 0,
    irrf_amount             NUMERIC(19,2) NOT NULL DEFAULT 0,
    net_amount              NUMERIC(19,2) NOT NULL,
    -- A transação de receita nas finanças pessoais, quando lançada (Fase 6).
    personal_transaction_id BIGINT,
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT fk_withdrawals_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_withdrawals_company FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_withdrawals_partner FOREIGN KEY (partner_id) REFERENCES partners (id),
    CONSTRAINT fk_withdrawals_transaction FOREIGN KEY (personal_transaction_id) REFERENCES transactions (id),
    CONSTRAINT uk_withdrawals_transaction UNIQUE (personal_transaction_id),
    CONSTRAINT ck_withdrawals_kind CHECK (kind IN ('PRO_LABORE', 'DISTRIBUICAO_LUCROS')),
    CONSTRAINT ck_withdrawals_pro_labore_competence CHECK (kind <> 'PRO_LABORE' OR competence_month IS NOT NULL),
    CONSTRAINT ck_withdrawals_competence_first_day CHECK (EXTRACT(DAY FROM competence_month) = 1),
    CONSTRAINT ck_withdrawals_gross CHECK (gross_amount > 0),
    CONSTRAINT ck_withdrawals_withheld CHECK (inss_amount >= 0 AND irrf_amount >= 0),
    CONSTRAINT ck_withdrawals_net CHECK (net_amount = gross_amount - inss_amount - irrf_amount AND net_amount > 0)
);

CREATE INDEX idx_withdrawals_user_id ON withdrawals (user_id);
CREATE INDEX idx_withdrawals_company_id ON withdrawals (company_id, payment_date);
-- Um pró-labore por sócio por mês da folha, mesmo com dois cliques simultâneos.
CREATE UNIQUE INDEX uk_withdrawals_pro_labore ON withdrawals (partner_id, competence_month) WHERE kind = 'PRO_LABORE';
