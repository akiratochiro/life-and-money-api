package com.akiratochiro.life_and_money_api.taxengine;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Arredondamento do motor. Alíquotas ficam em precisão cheia (10 dígitos significativos); cada valor
 * em dinheiro (um imposto, uma redução, o INSS) é arredondado para centavos, HALF_UP, como numa
 * folha de pagamento.
 */
public final class Money {

    public static final MathContext RATE = new MathContext(10, RoundingMode.HALF_UP);

    private Money() {
    }

    public static BigDecimal cents(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    /** Para cima, em centavos: um valor mínimo que precisa ser atingido (o pró-labore do Fator R). */
    public static BigDecimal centsUp(BigDecimal value) {
        return value.setScale(2, RoundingMode.CEILING);
    }

    public static BigDecimal max(BigDecimal a, BigDecimal b) {
        return a.compareTo(b) >= 0 ? a : b;
    }

    public static BigDecimal min(BigDecimal a, BigDecimal b) {
        return a.compareTo(b) <= 0 ? a : b;
    }
}
