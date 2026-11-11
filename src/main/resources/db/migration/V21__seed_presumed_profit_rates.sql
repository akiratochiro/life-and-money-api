-- Fase 6: os percentuais de presunção do lucro, que limitam a isenção do lucro distribuído pela ME/EPP
-- e pelo MEI sem contabilidade (LC 123, art. 14; Res. CGSN 140, art. 145). Como todo parâmetro, com
-- vigência e fonte (decisão 23). O texto oficial do art. 145 ainda será conferido
-- (UnverifiedLegalSource.RES_CGSN_140_ART_145).

ALTER TABLE tax_parameters DROP CONSTRAINT ck_tax_parameters_key;
ALTER TABLE tax_parameters ADD CONSTRAINT ck_tax_parameters_key CHECK (key IN (
    'MINIMUM_WAGE', 'INSS_CEILING', 'INSS_PRO_LABORE_RATE', 'IRRF_SIMPLIFIED_DISCOUNT', 'IRRF_DEPENDENT_DEDUCTION',
    'IRRF_REDUCTION_FULL_LIMIT', 'IRRF_REDUCTION_FULL_MAX', 'IRRF_REDUCTION_PARTIAL_LIMIT', 'IRRF_REDUCTION_A',
    'IRRF_REDUCTION_B', 'FATOR_R_THRESHOLD', 'DAS_MEI_INSS_RATE', 'DAS_MEI_ICMS', 'DAS_MEI_ISS', 'MEI_ANNUAL_LIMIT',
    'MEI_MONTHLY_LIMIT', 'MEI_TOLERANCE', 'SIMPLES_EPP_THRESHOLD', 'SIMPLES_SUBLIMIT', 'SIMPLES_CEILING',
    'SIMPLES_TOLERANCE', 'SIMPLES_ISS_CAP', 'DIVIDENDS_MONTHLY_EXEMPTION', 'DIVIDENDS_IRRF_RATE',
    'PRESUMED_PROFIT_SERVICES', 'PRESUMED_PROFIT_TRADE'));

INSERT INTO tax_parameters (key, valid_from, value, source) VALUES
('PRESUMED_PROFIT_SERVICES', '2026-01-01', 0.32, 'Lei 9.249/1995, art. 15, §1º, III, a; LC 123/2006, art. 14; Res. CGSN 140, art. 145'),
('PRESUMED_PROFIT_TRADE',    '2026-01-01', 0.08, 'Lei 9.249/1995, art. 15, caput; LC 123/2006, art. 14; Res. CGSN 140, art. 145');
