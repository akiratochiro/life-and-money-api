package com.akiratochiro.life_and_money_api.taxengine;

/**
 * Os parâmetros tributários que mudam com o tempo. Cada um tem valor e vigência na tabela
 * {@code tax_parameters}, com a fonte legal ao lado.
 */
public enum TaxParameter {
    MINIMUM_WAGE,
    INSS_CEILING,
    /** 11%: 20% do contribuinte individual menos a dedução de 9% (Lei 8.212, art. 21 e art. 30, §4º). */
    INSS_PRO_LABORE_RATE,
    IRRF_SIMPLIFIED_DISCOUNT,
    IRRF_DEPENDENT_DEDUCTION,
    /** Lei 15.270/2025: até este rendimento bruto, o IR fica zero. */
    IRRF_REDUCTION_FULL_LIMIT,
    /** A redução máxima na faixa de isenção (o IR de quem ganha exatamente o limite). */
    IRRF_REDUCTION_FULL_MAX,
    /** Acima deste rendimento bruto, não há redução. */
    IRRF_REDUCTION_PARTIAL_LIMIT,
    /** Redução parcial = A − B × rendimento bruto. */
    IRRF_REDUCTION_A,
    IRRF_REDUCTION_B,
    /** O limite legal do Fator R (28%). A meta da empresa é outra coisa e não decide o Anexo. */
    FATOR_R_THRESHOLD,
    DAS_MEI_INSS_RATE,
    DAS_MEI_ICMS,
    DAS_MEI_ISS,
    MEI_ANNUAL_LIMIT,
    /** No ano de abertura: este valor × meses de atividade. */
    MEI_MONTHLY_LIMIT,
    MEI_TOLERANCE,
    SIMPLES_EPP_THRESHOLD,
    SIMPLES_SUBLIMIT,
    SIMPLES_CEILING,
    SIMPLES_TOLERANCE,
    /** O ISS efetivo no Simples não passa de 5% (LC 123, art. 18, §1º-B). */
    SIMPLES_ISS_CAP,
    DIVIDENDS_MONTHLY_EXEMPTION,
    DIVIDENDS_IRRF_RATE
}
