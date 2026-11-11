-- Parâmetros tributários de 2026, conferidos com o usuário na Fase 4 (plano, seção 2). O app calcula
-- impostos a partir das competências de janeiro de 2026. As tabelas do Simples valem desde 2018
-- (LC 155/2016) e até a competência de 12/2026; as de 2027 (reforma tributária) entram numa
-- migration própria. Os limites do MEI de 2027 e 2028 já são lei (LC 238/2026).

INSERT INTO tax_parameters (key, valid_from, value, source) VALUES
('MINIMUM_WAGE',                 '2026-01-01', 1621.00,  'Decreto 12.797, de 23/12/2025, art. 1º'),
('INSS_CEILING',                 '2026-01-01', 8475.55,  'Portaria Interministerial MPS/MF nº 13, de 09/01/2026'),
('INSS_PRO_LABORE_RATE',         '2026-01-01', 0.11,     'Lei 8.212/1991, art. 21 e art. 30, §4º; Lei 10.666/2003, art. 4º'),
('IRRF_SIMPLIFIED_DISCOUNT',     '2026-01-01', 607.20,   'Receita Federal, Tributação de 2026 (desconto simplificado mensal)'),
('IRRF_DEPENDENT_DEDUCTION',     '2026-01-01', 189.59,   'Receita Federal, Tributação de 2026'),
('IRRF_REDUCTION_FULL_LIMIT',    '2026-01-01', 5000.00,  'Lei 15.270/2025, art. 2º (Lei 9.250, art. 3º-A)'),
('IRRF_REDUCTION_FULL_MAX',      '2026-01-01', 312.89,   'Lei 15.270/2025, art. 2º (Lei 9.250, art. 3º-A, I)'),
('IRRF_REDUCTION_PARTIAL_LIMIT', '2026-01-01', 7350.00,  'Lei 15.270/2025, art. 2º (Lei 9.250, art. 3º-A, II)'),
('IRRF_REDUCTION_A',             '2026-01-01', 978.62,   'Lei 15.270/2025: redução = 978,62 − 0,133145 × rendimento bruto'),
('IRRF_REDUCTION_B',             '2026-01-01', 0.133145, 'Lei 15.270/2025: redução = 978,62 − 0,133145 × rendimento bruto'),
('FATOR_R_THRESHOLD',            '2018-01-01', 0.28,     'LC 123/2006, art. 18, §§5º-J e 5º-M (LC 155/2016)'),
('DAS_MEI_INSS_RATE',            '2026-01-01', 0.05,     'LC 123/2006, art. 18-A, §3º, V; Res. CGSN 140, art. 101, I'),
('DAS_MEI_ICMS',                 '2026-01-01', 1.00,     'LC 123/2006, art. 18-A, §3º, V, b; aviso do Simples Nacional de 02/01/2026'),
('DAS_MEI_ISS',                  '2026-01-01', 5.00,     'LC 123/2006, art. 18-A, §3º, V, c; aviso do Simples Nacional de 02/01/2026'),
('MEI_ANNUAL_LIMIT',             '2018-01-01', 81000.00, 'LC 123/2006, art. 18-A, §1º'),
('MEI_ANNUAL_LIMIT',             '2027-01-01', 110000.00,'LC 238/2026, art. 2º (condicionado à LOA, art. 3º)'),
('MEI_ANNUAL_LIMIT',             '2028-01-01', 140000.00,'LC 238/2026, art. 1º e art. 10, I (condicionado à LOA, art. 3º)'),
('MEI_MONTHLY_LIMIT',            '2018-01-01', 6750.00,  'LC 123/2006, art. 18-A, §2º'),
('MEI_MONTHLY_LIMIT',            '2027-01-01', 9166.67,  'LC 238/2026, art. 2º, parágrafo único'),
('MEI_MONTHLY_LIMIT',            '2028-01-01', 11666.67, 'LC 238/2026, art. 1º e art. 10, I'),
('MEI_TOLERANCE',                '2018-01-01', 0.20,     'LC 123/2006, art. 18-A, §7º, III e IV (compilação não oficial; reconferir)'),
('SIMPLES_EPP_THRESHOLD',        '2018-01-01', 360000.00,'LC 123/2006, art. 3º, I'),
('SIMPLES_SUBLIMIT',             '2018-01-01', 3600000.00,'LC 123/2006, arts. 13-A e 19, §4º'),
('SIMPLES_CEILING',              '2018-01-01', 4800000.00,'LC 123/2006, art. 3º, II (LC 155/2016)'),
('SIMPLES_TOLERANCE',            '2018-01-01', 0.20,     'LC 123/2006, art. 3º, §§9º e 9º-A'),
('SIMPLES_ISS_CAP',              '2018-01-01', 0.05,     'LC 123/2006, art. 18, §1º-B; nota da faixa 5 do Anexo III'),
('DIVIDENDS_MONTHLY_EXEMPTION',  '2026-01-01', 50000.00, 'Lei 15.270/2025 (Lei 9.250, art. 6º-A)'),
('DIVIDENDS_IRRF_RATE',          '2026-01-01', 0.10,     'Lei 15.270/2025 (Lei 9.250, art. 6º-A)');

INSERT INTO simples_brackets (annex, bracket, valid_from, revenue_up_to, nominal_rate, deduction,
                              irpj_share, csll_share, cofins_share, pis_share, cpp_share, iss_share, source) VALUES
('III', 1, '2018-01-01',  180000.00, 0.06,  0,         0.04, 0.035, 0.1282, 0.0278, 0.434,  0.335, 'LC 123/2006, Anexo III (LC 155/2016)'),
('III', 2, '2018-01-01',  360000.00, 0.112, 9360.00,   0.04, 0.035, 0.1405, 0.0305, 0.434,  0.32,  'LC 123/2006, Anexo III (LC 155/2016)'),
('III', 3, '2018-01-01',  720000.00, 0.135, 17640.00,  0.04, 0.035, 0.1364, 0.0296, 0.434,  0.325, 'LC 123/2006, Anexo III (LC 155/2016)'),
('III', 4, '2018-01-01', 1800000.00, 0.16,  35640.00,  0.04, 0.035, 0.1364, 0.0296, 0.434,  0.325, 'LC 123/2006, Anexo III (LC 155/2016)'),
('III', 5, '2018-01-01', 3600000.00, 0.21,  125640.00, 0.04, 0.035, 0.1282, 0.0278, 0.434,  0.335, 'LC 123/2006, Anexo III (LC 155/2016); ISS efetivo limitado a 5%'),
('III', 6, '2018-01-01', 4800000.00, 0.33,  648000.00, 0.35, 0.15,  0.1603, 0.0347, 0.305,  0,     'LC 123/2006, Anexo III (LC 155/2016); ISS fora do DAS'),
('V',   1, '2018-01-01',  180000.00, 0.155, 0,         0.25, 0.15,  0.141,  0.0305, 0.2885, 0.14,  'LC 123/2006, Anexo V (LC 155/2016)'),
('V',   2, '2018-01-01',  360000.00, 0.18,  4500.00,   0.23, 0.15,  0.141,  0.0305, 0.2785, 0.17,  'LC 123/2006, Anexo V (LC 155/2016)'),
('V',   3, '2018-01-01',  720000.00, 0.195, 9900.00,   0.24, 0.15,  0.1492, 0.0323, 0.2385, 0.19,  'LC 123/2006, Anexo V (LC 155/2016)'),
('V',   4, '2018-01-01', 1800000.00, 0.205, 17100.00,  0.21, 0.15,  0.1574, 0.0341, 0.2385, 0.21,  'LC 123/2006, Anexo V (LC 155/2016)'),
('V',   5, '2018-01-01', 3600000.00, 0.23,  62100.00,  0.23, 0.125, 0.141,  0.0305, 0.2385, 0.235, 'LC 123/2006, Anexo V (LC 155/2016)'),
('V',   6, '2018-01-01', 4800000.00, 0.305, 540000.00, 0.35, 0.155, 0.1644, 0.0356, 0.295,  0,     'LC 123/2006, Anexo V (LC 155/2016); ISS fora do DAS');

INSERT INTO irrf_brackets (valid_from, bracket, income_up_to, rate, deduction, source) VALUES
('2026-01-01', 1, 2428.80, 0,     0,      'Lei 15.191/2025, art. 2º; Receita Federal, Tributação de 2026'),
('2026-01-01', 2, 2826.65, 0.075, 182.16, 'Lei 15.191/2025, art. 2º; Receita Federal, Tributação de 2026'),
('2026-01-01', 3, 3751.05, 0.15,  394.16, 'Lei 15.191/2025, art. 2º; Receita Federal, Tributação de 2026'),
('2026-01-01', 4, 4664.68, 0.225, 675.49, 'Lei 15.191/2025, art. 2º; Receita Federal, Tributação de 2026'),
('2026-01-01', 5, NULL,    0.275, 908.73, 'Lei 15.191/2025, art. 2º; Receita Federal, Tributação de 2026');
