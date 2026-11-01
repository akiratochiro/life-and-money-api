-- Contas a pagar e a receber previstas. A conta (scheduled_bills) é a identidade, que não muda;
-- os termos (scheduled_bill_terms) são o que muda com o tempo e por isso têm vigência, como o
-- orçamento. As ocorrências não são gravadas: são calculadas a partir das duas tabelas.

CREATE TABLE scheduled_bills (
    id          BIGSERIAL    PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    recurrence  VARCHAR(20)  NOT NULL,
    start_month DATE         NOT NULL,
    end_month   DATE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_scheduled_bills_user
        FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_scheduled_bills_recurrence
        CHECK (recurrence IN ('ONCE', 'MONTHLY', 'YEARLY')),
    CONSTRAINT ck_scheduled_bills_start_month_first_day
        CHECK (EXTRACT(DAY FROM start_month) = 1),
    CONSTRAINT ck_scheduled_bills_end_month_first_day
        CHECK (EXTRACT(DAY FROM end_month) = 1),
    CONSTRAINT ck_scheduled_bills_period
        CHECK (end_month >= start_month),
    CONSTRAINT ck_scheduled_bills_once_single_month
        CHECK (recurrence <> 'ONCE' OR end_month = start_month)
);

CREATE INDEX idx_scheduled_bills_user_id
    ON scheduled_bills (user_id);

CREATE TABLE scheduled_bill_terms (
    id          BIGSERIAL     PRIMARY KEY,
    user_id     BIGINT        NOT NULL,
    bill_id     BIGINT        NOT NULL,
    category_id BIGINT        NOT NULL,
    name        VARCHAR(100)  NOT NULL,
    amount      NUMERIC(19,2) NOT NULL,
    due_day     INTEGER       NOT NULL,
    valid_from  DATE          NOT NULL,
    valid_to    DATE,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT fk_scheduled_bill_terms_user
        FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_scheduled_bill_terms_bill
        FOREIGN KEY (bill_id) REFERENCES scheduled_bills (id),
    CONSTRAINT fk_scheduled_bill_terms_category
        FOREIGN KEY (category_id) REFERENCES categories (id),
    CONSTRAINT ck_scheduled_bill_terms_amount_positive
        CHECK (amount > 0),
    CONSTRAINT ck_scheduled_bill_terms_due_day
        CHECK (due_day BETWEEN 1 AND 31),
    CONSTRAINT ck_scheduled_bill_terms_valid_from_first_day
        CHECK (EXTRACT(DAY FROM valid_from) = 1),
    CONSTRAINT ck_scheduled_bill_terms_valid_to_first_day
        CHECK (EXTRACT(DAY FROM valid_to) = 1),
    CONSTRAINT ck_scheduled_bill_terms_valid_period
        CHECK (valid_to >= valid_from)
);

CREATE INDEX idx_scheduled_bill_terms_user_id
    ON scheduled_bill_terms (user_id);

CREATE INDEX idx_scheduled_bill_terms_category_id
    ON scheduled_bill_terms (category_id);

CREATE UNIQUE INDEX uk_scheduled_bill_terms_active
    ON scheduled_bill_terms (bill_id) WHERE valid_to IS NULL;
