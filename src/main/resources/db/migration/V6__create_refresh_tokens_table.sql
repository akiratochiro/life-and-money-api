-- O refresh token é um valor opaco e aleatório, guardado aqui apenas como hash SHA-256.
-- Um dump do banco, portanto, não entrega tokens utilizáveis: é a mesma razão de guardar
-- o hash da senha e não a senha. A linha também é o que permite revogar uma sessão, algo
-- que um JWT autocontido não permitiria sem uma lista no servidor de qualquer maneira.
CREATE TABLE refresh_tokens (
    id         BIGSERIAL   PRIMARY KEY,
    user_id    BIGINT      NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    issued_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    CONSTRAINT fk_refresh_tokens_user
        FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uk_refresh_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT ck_refresh_tokens_period
        CHECK (expires_at > issued_at)
);

-- Usado para revogar de uma vez todas as sessões de um usuário.
CREATE INDEX idx_refresh_tokens_user_id
    ON refresh_tokens (user_id);
