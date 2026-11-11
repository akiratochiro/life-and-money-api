package com.akiratochiro.life_and_money_api.company;

/**
 * Quando a receita conta. Por enquanto só competência (a data de competência da nota); o caixa
 * já é aceito pelo banco para não precisar de migration quando chegar.
 */
public enum RevenueBasis {
    COMPETENCIA,
    CAIXA
}
