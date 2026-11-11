package com.akiratochiro.life_and_money_api.taxengine;

import java.math.BigDecimal;

/**
 * Uma faixa de um Anexo: até que RBT12 ela vai, a alíquota nominal, a parcela a deduzir e a parte
 * de cada tributo na alíquota (a repartição). As frações somam 1.
 */
public record SimplesBracket(
        Annex annex,
        int bracket,
        BigDecimal revenueUpTo,
        BigDecimal nominalRate,
        BigDecimal deduction,
        BigDecimal irpjShare,
        BigDecimal csllShare,
        BigDecimal cofinsShare,
        BigDecimal pisShare,
        BigDecimal cppShare,
        BigDecimal issShare
) {
}
