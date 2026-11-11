-- Obrigações tributárias e pagamentos (Fase 4). As do sistema (DAS, DAS-MEI, DARF previdenciário,
-- DEFIS, DASN-SIMEI) são uma linha por empresa, sem período: o enquadramento de cada mês decide se
-- elas valem, então mudar de regime (ou desfazer a mudança) não deixa nada para sincronizar. Só as
-- personalizadas têm período. Os vencimentos são calculados; só o pagamento vira linha.

CREATE TABLE tax_obligations (
    id           BIGSERIAL     PRIMARY KEY,
    user_id      BIGINT        NOT NULL,
    company_id   BIGINT        NOT NULL,
    kind         VARCHAR(30)   NOT NULL,
    name         VARCHAR(100)  NOT NULL,
    recurrence   VARCHAR(10)   NOT NULL,
    due_day      INTEGER       NOT NULL,
    due_month    INTEGER,
    due_rule     VARCHAR(12)   NOT NULL,
    fixed_amount NUMERIC(19,2),
    system       BOOLEAN       NOT NULL,
    active       BOOLEAN       NOT NULL DEFAULT true,
    start_month  DATE,
    end_month    DATE,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT fk_tax_obligations_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_tax_obligations_company FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT ck_tax_obligations_kind CHECK (kind IN ('DAS', 'DAS_MEI', 'DARF_PREVIDENCIARIO', 'DEFIS', 'DASN_SIMEI', 'CUSTOM')),
    CONSTRAINT ck_tax_obligations_recurrence CHECK (recurrence IN ('MONTHLY', 'YEARLY', 'ONCE')),
    CONSTRAINT ck_tax_obligations_due_day CHECK (due_day BETWEEN 1 AND 31),
    CONSTRAINT ck_tax_obligations_due_month CHECK (due_month BETWEEN 1 AND 12),
    CONSTRAINT ck_tax_obligations_yearly CHECK (recurrence <> 'YEARLY' OR due_month IS NOT NULL),
    -- DAS adia para o próximo dia útil; DARF antecipa para o anterior; personalizadas escolhem.
    CONSTRAINT ck_tax_obligations_due_rule CHECK (due_rule IN ('POSTPONE', 'ANTICIPATE', 'NONE')),
    CONSTRAINT ck_tax_obligations_fixed_amount CHECK (fixed_amount > 0),
    CONSTRAINT ck_tax_obligations_system CHECK (system = (kind <> 'CUSTOM')),
    CONSTRAINT ck_tax_obligations_custom_period CHECK (system OR start_month IS NOT NULL),
    CONSTRAINT ck_tax_obligations_start_first_day CHECK (EXTRACT(DAY FROM start_month) = 1),
    CONSTRAINT ck_tax_obligations_end_first_day CHECK (EXTRACT(DAY FROM end_month) = 1),
    CONSTRAINT ck_tax_obligations_period CHECK (end_month >= start_month)
);

CREATE INDEX idx_tax_obligations_user_id ON tax_obligations (user_id);
CREATE INDEX idx_tax_obligations_company_id ON tax_obligations (company_id);
CREATE UNIQUE INDEX uk_tax_obligations_system ON tax_obligations (company_id, kind) WHERE system;

-- Um pagamento por obrigação e competência. O valor e a data pagos moram na despesa de impostos,
-- como nas contas pessoais moram na transação: editar a despesa corrige o pagamento. As declarações
-- (DEFIS, DASN-SIMEI) não têm valor: guardam só o dia da entrega.
CREATE TABLE tax_payments (
    id               BIGSERIAL     PRIMARY KEY,
    user_id          BIGINT        NOT NULL,
    obligation_id    BIGINT        NOT NULL,
    competence_month DATE          NOT NULL,
    -- O previsto no momento do pagamento: uma nota lançada depois não muda a diferença exibida.
    expected_amount  NUMERIC(19,2),
    expense_id       BIGINT,
    delivered_on     DATE,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT fk_tax_payments_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_tax_payments_obligation FOREIGN KEY (obligation_id) REFERENCES tax_obligations (id),
    CONSTRAINT fk_tax_payments_expense FOREIGN KEY (expense_id) REFERENCES company_expenses (id),
    CONSTRAINT uk_tax_payments_occurrence UNIQUE (obligation_id, competence_month),
    CONSTRAINT uk_tax_payments_expense UNIQUE (expense_id),
    CONSTRAINT ck_tax_payments_competence_first_day CHECK (EXTRACT(DAY FROM competence_month) = 1),
    CONSTRAINT ck_tax_payments_one_kind CHECK ((expense_id IS NULL) <> (delivered_on IS NULL))
);

CREATE INDEX idx_tax_payments_user_id ON tax_payments (user_id);

-- As obrigações do sistema para as empresas que já existem; as novas ganham as mesmas pelo evento.
INSERT INTO tax_obligations (user_id, company_id, kind, name, recurrence, due_day, due_month, due_rule, system)
SELECT c.user_id, c.id, o.kind, o.name, o.recurrence, o.due_day, o.due_month, o.due_rule, true
  FROM companies c
 CROSS JOIN (VALUES
        ('DAS',                 'DAS do Simples Nacional',                   'MONTHLY', 20, NULL::INTEGER, 'POSTPONE'),
        ('DAS_MEI',             'DAS-MEI',                                   'MONTHLY', 20, NULL,          'POSTPONE'),
        ('DARF_PREVIDENCIARIO', 'DARF do pró-labore (INSS e IRRF)',          'MONTHLY', 20, NULL,          'ANTICIPATE'),
        ('DEFIS',               'DEFIS (declaração anual do Simples)',       'YEARLY',  31, 3,             'NONE'),
        ('DASN_SIMEI',          'DASN-SIMEI (declaração anual do MEI)',      'YEARLY',  31, 5,             'NONE')
       ) AS o(kind, name, recurrence, due_day, due_month, due_rule);
