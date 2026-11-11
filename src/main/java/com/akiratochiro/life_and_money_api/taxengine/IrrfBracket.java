package com.akiratochiro.life_and_money_api.taxengine;

import java.math.BigDecimal;

/** Uma faixa da tabela mensal do IRRF. {@code incomeUpTo} nulo é a última, sem teto. */
public record IrrfBracket(int bracket, BigDecimal incomeUpTo, BigDecimal rate, BigDecimal deduction) {
}
