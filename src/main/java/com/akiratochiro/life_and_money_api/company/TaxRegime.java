package com.akiratochiro.life_and_money_api.company;

/**
 * Regime tributário. O cálculo automático de impostos existe só para SIMEI e Simples Nacional;
 * Lucro Presumido e Lucro Real são cadastráveis, mas os impostos deles são lançados à mão.
 */
public enum TaxRegime {
    SIMEI,
    SIMPLES_NACIONAL,
    LUCRO_PRESUMIDO,
    LUCRO_REAL
}
