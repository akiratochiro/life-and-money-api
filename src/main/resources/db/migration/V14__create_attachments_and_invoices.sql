-- Arquivos guardados no próprio Postgres (decisão 19), numa tabela reutilizável: as notas fiscais
-- agora, os comprovantes de despesa depois. Ficam fora das tabelas que os usam para que listar notas
-- nunca carregue os bytes.

CREATE TABLE attachments (
    id           BIGSERIAL    PRIMARY KEY,
    user_id      BIGINT       NOT NULL,
    filename     VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes   INTEGER      NOT NULL,
    sha256       CHAR(64)     NOT NULL,
    content      BYTEA        NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_attachments_user
        FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_attachments_content_type
        CHECK (content_type IN ('application/xml', 'application/pdf', 'image/jpeg', 'image/png')),
    -- 2 MB por arquivo; a cota por usuário fica no AttachmentService.
    CONSTRAINT ck_attachments_size
        CHECK (size_bytes BETWEEN 1 AND 2097152)
);

CREATE INDEX idx_attachments_user_id
    ON attachments (user_id);

-- Notas fiscais de serviço emitidas pela empresa (NFS-e). Os valores vêm como emitidos: o XML
-- assinado é a fonte da verdade, e o próprio leiaute se contradiz sobre como recalcular alguns.
CREATE TABLE invoices (
    id                            BIGSERIAL     PRIMARY KEY,
    user_id                       BIGINT        NOT NULL,
    company_id                    BIGINT        NOT NULL,
    attachment_id                 BIGINT        NOT NULL,
    source                        VARCHAR(10)   NOT NULL,
    number                        VARCHAR(20)   NOT NULL,
    -- Os 50 dígitos depois de "NFS" no Id da nota. A nota lida do PDF pode não ter.
    access_key                    CHAR(50),
    issue_date                    DATE          NOT NULL,
    -- É pela competência que a receita conta.
    competence_date               DATE          NOT NULL,
    service_amount                NUMERIC(19,2) NOT NULL,
    -- Desconto incondicional: não é receita bruta (LC 123, art. 3º, §1º).
    unconditional_discount_amount NUMERIC(19,2) NOT NULL DEFAULT 0,
    net_amount                    NUMERIC(19,2) NOT NULL,
    iss_withheld_amount           NUMERIC(19,2) NOT NULL DEFAULT 0,
    irrf_withheld_amount          NUMERIC(19,2) NOT NULL DEFAULT 0,
    pis_withheld_amount           NUMERIC(19,2) NOT NULL DEFAULT 0,
    cofins_withheld_amount        NUMERIC(19,2) NOT NULL DEFAULT 0,
    csll_withheld_amount          NUMERIC(19,2) NOT NULL DEFAULT 0,
    inss_withheld_amount          NUMERIC(19,2) NOT NULL DEFAULT 0,
    taker_name                    VARCHAR(255),
    -- CNPJ, CPF ou NIF (documento de estrangeiro, até 40 caracteres).
    taker_document                VARCHAR(40),
    -- A nota que esta substitui; ao salvar, a substituída vira CANCELADA.
    replaced_access_key           CHAR(50),
    status                        VARCHAR(10)   NOT NULL DEFAULT 'ATIVA',
    created_at                    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at                    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT fk_invoices_user
        FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_invoices_company
        FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_invoices_attachment
        FOREIGN KEY (attachment_id) REFERENCES attachments (id),
    CONSTRAINT uk_invoices_attachment
        UNIQUE (attachment_id),
    -- A mesma nota não entra duas vezes na empresa, nem com dois cliques simultâneos. Notas sem
    -- chave (lidas do PDF ou digitadas) não colidem: NULL nunca é igual a NULL num UNIQUE.
    CONSTRAINT uk_invoices_access_key
        UNIQUE (company_id, access_key),
    CONSTRAINT ck_invoices_source
        CHECK (source IN ('XML', 'PDF', 'MANUAL')),
    CONSTRAINT ck_invoices_status
        CHECK (status IN ('ATIVA', 'CANCELADA')),
    CONSTRAINT ck_invoices_access_key
        CHECK (access_key ~ '^[0-9]{50}$'),
    CONSTRAINT ck_invoices_replaced_access_key
        CHECK (replaced_access_key ~ '^[0-9]{50}$'),
    CONSTRAINT ck_invoices_service_amount
        CHECK (service_amount > 0),
    CONSTRAINT ck_invoices_discount
        CHECK (unconditional_discount_amount >= 0 AND unconditional_discount_amount <= service_amount),
    CONSTRAINT ck_invoices_net_amount
        CHECK (net_amount >= 0),
    CONSTRAINT ck_invoices_withheld
        CHECK (iss_withheld_amount >= 0 AND irrf_withheld_amount >= 0 AND pis_withheld_amount >= 0
            AND cofins_withheld_amount >= 0 AND csll_withheld_amount >= 0 AND inss_withheld_amount >= 0)
);

CREATE INDEX idx_invoices_user_id
    ON invoices (user_id);

CREATE INDEX idx_invoices_company_competence
    ON invoices (company_id, competence_date);
