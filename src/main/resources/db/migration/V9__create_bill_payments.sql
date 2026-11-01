-- Só o pagamento de uma ocorrência vira linha: as ocorrências são calculadas. O valor e a data
-- pagos ficam na transação ligada, não aqui.

CREATE TABLE bill_payments (
    id             BIGSERIAL    PRIMARY KEY,
    user_id        BIGINT       NOT NULL,
    bill_id        BIGINT       NOT NULL,
    due_month      DATE         NOT NULL,
    transaction_id BIGINT       NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_bill_payments_user
        FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_bill_payments_bill
        FOREIGN KEY (bill_id) REFERENCES scheduled_bills (id),
    CONSTRAINT fk_bill_payments_transaction
        FOREIGN KEY (transaction_id) REFERENCES transactions (id),
    CONSTRAINT ck_bill_payments_due_month_first_day
        CHECK (EXTRACT(DAY FROM due_month) = 1),
    -- Uma ocorrência por conta e por mês: pagar duas vezes é barrado pelo banco, mesmo com dois
    -- cliques simultâneos.
    CONSTRAINT uk_bill_payments_occurrence
        UNIQUE (bill_id, due_month),
    CONSTRAINT uk_bill_payments_transaction
        UNIQUE (transaction_id)
);

CREATE INDEX idx_bill_payments_user_id
    ON bill_payments (user_id);
