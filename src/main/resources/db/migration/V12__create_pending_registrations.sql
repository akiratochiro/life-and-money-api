-- Cadastro esperando a confirmação do e-mail. A conta (users) só nasce quando o link do e-mail é
-- usado: até lá, ninguém entra com esse e-mail, e quem cadastrou o e-mail de outra pessoa não
-- consegue nada sem acesso à caixa dela.
--
-- Uma linha por e-mail: um cadastro novo para o mesmo e-mail substitui o anterior (nome, senha e
-- link), e o link antigo para de valer. Como no reset de senha, só o hash SHA-256 do token fica aqui.
-- Não há user_id, então a exclusão de conta não precisa saber desta tabela; as linhas vencidas são
-- apagadas a cada cadastro novo.
CREATE TABLE pending_registrations (
    id            BIGSERIAL    PRIMARY KEY,
    email         VARCHAR(255) NOT NULL,
    name          VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    token_hash    VARCHAR(64)  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_pending_registrations_email UNIQUE (email),
    CONSTRAINT uk_pending_registrations_token_hash UNIQUE (token_hash),
    CONSTRAINT ck_pending_registrations_period CHECK (expires_at > created_at)
);
