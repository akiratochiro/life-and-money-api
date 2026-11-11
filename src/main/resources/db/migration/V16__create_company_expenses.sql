-- Despesas da empresa (decisão 21). A fixa repete o desenho das contas pessoais (decisão 9): a
-- identidade (company_recurring_expenses) não muda, os termos têm vigência, os meses são calculados
-- e só o pagamento vira linha em company_expenses. A avulsa é um fato: uma despesa já paga.

CREATE TABLE company_recurring_expenses (
    id          BIGSERIAL    PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    company_id  BIGINT       NOT NULL,
    start_month DATE         NOT NULL,
    end_month   DATE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_company_recurring_expenses_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_company_recurring_expenses_company FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT ck_company_recurring_expenses_start_first_day CHECK (EXTRACT(DAY FROM start_month) = 1),
    CONSTRAINT ck_company_recurring_expenses_end_first_day CHECK (EXTRACT(DAY FROM end_month) = 1),
    CONSTRAINT ck_company_recurring_expenses_period CHECK (end_month >= start_month)
);

CREATE INDEX idx_company_recurring_expenses_user_id ON company_recurring_expenses (user_id);
CREATE INDEX idx_company_recurring_expenses_company_id ON company_recurring_expenses (company_id);

CREATE TABLE company_recurring_expense_terms (
    id                   BIGSERIAL     PRIMARY KEY,
    user_id              BIGINT        NOT NULL,
    recurring_expense_id BIGINT        NOT NULL,
    category_id          BIGINT        NOT NULL,
    name                 VARCHAR(100)  NOT NULL,
    amount               NUMERIC(19,2) NOT NULL,
    due_day              INTEGER       NOT NULL,
    valid_from           DATE          NOT NULL,
    valid_to             DATE,
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT fk_company_recurring_expense_terms_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_company_recurring_expense_terms_recurring
        FOREIGN KEY (recurring_expense_id) REFERENCES company_recurring_expenses (id),
    CONSTRAINT fk_company_recurring_expense_terms_category
        FOREIGN KEY (category_id) REFERENCES company_expense_categories (id),
    CONSTRAINT ck_company_recurring_expense_terms_amount CHECK (amount > 0),
    CONSTRAINT ck_company_recurring_expense_terms_due_day CHECK (due_day BETWEEN 1 AND 31),
    CONSTRAINT ck_company_recurring_expense_terms_from_first_day CHECK (EXTRACT(DAY FROM valid_from) = 1),
    CONSTRAINT ck_company_recurring_expense_terms_to_first_day CHECK (EXTRACT(DAY FROM valid_to) = 1),
    CONSTRAINT ck_company_recurring_expense_terms_period CHECK (valid_to >= valid_from)
);

CREATE INDEX idx_company_recurring_expense_terms_user_id ON company_recurring_expense_terms (user_id);
CREATE INDEX idx_company_recurring_expense_terms_category_id ON company_recurring_expense_terms (category_id);
CREATE UNIQUE INDEX uk_company_recurring_expense_terms_active
    ON company_recurring_expense_terms (recurring_expense_id) WHERE valid_to IS NULL;

-- O que foi pago: as avulsas e os pagamentos das fixas (estes com a fixa e o mês).
CREATE TABLE company_expenses (
    id                   BIGSERIAL     PRIMARY KEY,
    user_id              BIGINT        NOT NULL,
    company_id           BIGINT        NOT NULL,
    category_id          BIGINT        NOT NULL,
    name                 VARCHAR(100)  NOT NULL,
    amount               NUMERIC(19,2) NOT NULL,
    expense_date         DATE          NOT NULL,
    attachment_id        BIGINT,
    recurring_expense_id BIGINT,
    occurrence_month     DATE,
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT fk_company_expenses_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_company_expenses_company FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_company_expenses_category FOREIGN KEY (category_id) REFERENCES company_expense_categories (id),
    CONSTRAINT fk_company_expenses_attachment FOREIGN KEY (attachment_id) REFERENCES attachments (id),
    CONSTRAINT fk_company_expenses_recurring
        FOREIGN KEY (recurring_expense_id) REFERENCES company_recurring_expenses (id),
    CONSTRAINT uk_company_expenses_attachment UNIQUE (attachment_id),
    -- Um pagamento por mês de cada fixa, mesmo com dois cliques simultâneos.
    CONSTRAINT uk_company_expenses_occurrence UNIQUE (recurring_expense_id, occurrence_month),
    CONSTRAINT ck_company_expenses_amount CHECK (amount > 0),
    CONSTRAINT ck_company_expenses_occurrence
        CHECK ((recurring_expense_id IS NULL) = (occurrence_month IS NULL)),
    CONSTRAINT ck_company_expenses_occurrence_first_day CHECK (EXTRACT(DAY FROM occurrence_month) = 1)
);

CREATE INDEX idx_company_expenses_user_id ON company_expenses (user_id);
CREATE INDEX idx_company_expenses_company_date ON company_expenses (company_id, expense_date);
CREATE INDEX idx_company_expenses_category_id ON company_expenses (category_id);
