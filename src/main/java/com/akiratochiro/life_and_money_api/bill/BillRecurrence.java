package com.akiratochiro.life_and_money_api.bill;

/**
 * Com que frequência a conta se repete. Todas têm no máximo uma ocorrência por mês, o que faz de
 * {@code (conta, mês)} a identidade de cada ocorrência. Uma recorrência semanal quebraria isso.
 */
public enum BillRecurrence {
    /** Só no mês de início. */
    ONCE,
    /** Todo mês, do início ao fim. */
    MONTHLY,
    /** Uma vez por ano, no mesmo mês do início. */
    YEARLY
}
