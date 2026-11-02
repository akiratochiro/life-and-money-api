-- Cor e ícone de cada categoria, de uma paleta e de uma lista fechadas (CategoryColor e
-- CategoryIcon). As categorias que já existem recebem um par a partir do nome, para as padrão,
-- ou do tipo, para as criadas pelo usuário.

ALTER TABLE categories ADD COLUMN color VARCHAR(20);
ALTER TABLE categories ADD COLUMN icon VARCHAR(30);

UPDATE categories SET color = 'MINT',     icon = 'SALARY'     WHERE name = 'Salário'       AND type = 'INCOME';
UPDATE categories SET color = 'LIME',     icon = 'CART'       WHERE name = 'Mercado'       AND type = 'EXPENSE';
UPDATE categories SET color = 'SKY',      icon = 'TRANSPORT'  WHERE name = 'Transporte'    AND type = 'EXPENSE';
UPDATE categories SET color = 'PEACH',    icon = 'HOME'       WHERE name = 'Moradia'       AND type = 'EXPENSE';
UPDATE categories SET color = 'LAVENDER', icon = 'LEISURE'    WHERE name = 'Lazer'         AND type = 'EXPENSE';
UPDATE categories SET color = 'PINK',     icon = 'PIGGY_BANK' WHERE name = 'Investimentos' AND type = 'SAVING';

UPDATE categories SET color = 'MINT',  icon = 'COIN'       WHERE color IS NULL AND type = 'INCOME';
UPDATE categories SET color = 'CORAL', icon = 'OTHER'      WHERE color IS NULL AND type = 'EXPENSE';
UPDATE categories SET color = 'PINK',  icon = 'PIGGY_BANK' WHERE color IS NULL AND type = 'SAVING';

ALTER TABLE categories ALTER COLUMN color SET NOT NULL;
ALTER TABLE categories ALTER COLUMN icon SET NOT NULL;

ALTER TABLE categories ADD CONSTRAINT ck_categories_color CHECK (color IN (
    'ROSE', 'PINK', 'PEACH', 'CORAL', 'AMBER', 'LIME', 'MINT', 'TEAL', 'SKY', 'LAVENDER', 'PLUM', 'SAND'));

ALTER TABLE categories ADD CONSTRAINT ck_categories_icon CHECK (icon IN (
    'HOME', 'CART', 'FOOD', 'TRANSPORT', 'CAR', 'HEALTH', 'EDUCATION', 'LEISURE', 'TRAVEL', 'PET',
    'CLOTHES', 'BILLS', 'PHONE', 'GIFT', 'SALARY', 'INVESTMENT', 'PIGGY_BANK', 'COIN', 'HEART', 'OTHER'));
