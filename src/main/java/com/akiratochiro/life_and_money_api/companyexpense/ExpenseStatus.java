package com.akiratochiro.life_and_money_api.companyexpense;

/**
 * Situação de um mês de despesa. Atrasada conta pelo dia do calendário, sem a regra de dia útil das
 * contas pessoais: despesa da empresa não é boleto com prorrogação (decisão 21).
 */
public enum ExpenseStatus {
    PAID,
    PENDING,
    OVERDUE
}
