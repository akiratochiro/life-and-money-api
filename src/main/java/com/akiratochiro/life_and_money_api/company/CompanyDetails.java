package com.akiratochiro.life_and_money_api.company;

/**
 * A empresa com o que a tela precisa junto: o enquadramento que vale no mês corrente e se a data
 * de abertura ainda pode mudar.
 */
public record CompanyDetails(Company company, CompanyRegime currentRegime, boolean openingDateEditable) {
}
