-- Token de redefinição de senha, enviado por e-mail como parte de um link. Como no refresh
-- token, só o hash SHA-256 fica aqui: um dump do banco não entrega links utilizáveis.
--
-- used_at e revoked_at ficam separados, em vez de apagar a linha, para dar para responder depois
-- por que um link não funcionou: foi usado, ou um pedido mais novo o invalidou.
CREATE TABLE password_reset_tokens (
    id         BIGSERIAL   PRIMARY KEY,
    user_id    BIGINT      NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    used_at    TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    CONSTRAINT fk_password_reset_tokens_user
        FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uk_password_reset_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT ck_password_reset_tokens_period
        CHECK (expires_at > created_at)
);

-- Usado para invalidar de uma vez os tokens anteriores do usuário.
CREATE INDEX idx_password_reset_tokens_user_id
    ON password_reset_tokens (user_id);
