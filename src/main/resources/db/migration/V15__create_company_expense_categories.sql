-- Categorias de despesa da empresa (decisão 21), separadas das pessoais e por empresa. Cada empresa
-- nasce com as padrão (CompanyCreatedEvent); as que já existem recebem as mesmas aqui.
-- "Impostos" é do sistema: recebe as despesas criadas ao pagar um imposto (Fase 4) e não pode ser
-- escolhida à mão, renomeada nem arquivada.

CREATE TABLE company_expense_categories (
    id                BIGSERIAL    PRIMARY KEY,
    user_id           BIGINT       NOT NULL,
    company_id        BIGINT       NOT NULL,
    name              VARCHAR(100) NOT NULL,
    color             VARCHAR(20)  NOT NULL,
    icon              VARCHAR(30)  NOT NULL,
    -- Entra na folha de salários do Fator R (salários, encargos, FGTS).
    counts_as_payroll BOOLEAN      NOT NULL DEFAULT false,
    system_key        VARCHAR(20),
    archived          BOOLEAN      NOT NULL DEFAULT false,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_company_expense_categories_user
        FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_company_expense_categories_company
        FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT ck_company_expense_categories_system_key
        CHECK (system_key IN ('TAXES')),
    CONSTRAINT ck_company_expense_categories_color CHECK (color IN (
        'ROSE', 'PINK', 'PEACH', 'CORAL', 'AMBER', 'LIME', 'MINT', 'TEAL', 'SKY', 'LAVENDER', 'PLUM', 'SAND')),
    CONSTRAINT ck_company_expense_categories_icon CHECK (icon IN (
        'HOME', 'CART', 'FOOD', 'TRANSPORT', 'CAR', 'HEALTH', 'EDUCATION', 'LEISURE', 'TRAVEL', 'PET',
        'CLOTHES', 'BILLS', 'PHONE', 'GIFT', 'SALARY', 'INVESTMENT', 'PIGGY_BANK', 'COIN', 'HEART', 'OTHER')),
    -- A categoria do sistema nunca é arquivada.
    CONSTRAINT ck_company_expense_categories_system_active
        CHECK (system_key IS NULL OR archived = false)
);

CREATE INDEX idx_company_expense_categories_user_id
    ON company_expense_categories (user_id);

CREATE INDEX idx_company_expense_categories_company_id
    ON company_expense_categories (company_id);

CREATE UNIQUE INDEX uk_company_expense_categories_active
    ON company_expense_categories (company_id, LOWER(name)) WHERE archived = false;

CREATE UNIQUE INDEX uk_company_expense_categories_system
    ON company_expense_categories (company_id, system_key) WHERE system_key IS NOT NULL;

-- As mesmas padrão do DefaultExpenseCategories, para as empresas cadastradas antes desta migration.
INSERT INTO company_expense_categories (user_id, company_id, name, color, icon, counts_as_payroll, system_key)
SELECT c.user_id, c.id, d.name, d.color, d.icon, d.payroll, d.system_key
  FROM companies c
 CROSS JOIN (VALUES
        ('Aluguel',             'PEACH',    'HOME',   false, NULL),
        ('Equipamentos',        'SKY',      'CART',   false, NULL),
        ('Contador',            'LAVENDER', 'BILLS',  false, NULL),
        ('Software',            'TEAL',     'PHONE',  false, NULL),
        ('Salários e encargos', 'MINT',     'SALARY', true,  NULL),
        ('Outros',              'SAND',     'OTHER',  false, NULL),
        ('Impostos',            'AMBER',    'COIN',   false, 'TAXES')
       ) AS d(name, color, icon, payroll, system_key);
