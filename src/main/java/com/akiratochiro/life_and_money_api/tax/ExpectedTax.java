package com.akiratochiro.life_and_money_api.tax;

import java.math.BigDecimal;

/** O previsto de uma ocorrência: o valor (ou nada, com o motivo) e a memória de cálculo. */
record ExpectedTax(BigDecimal amount, String note, TaxCalculation calculation) {

    static ExpectedTax none(String note) {
        return new ExpectedTax(null, note, null);
    }
}
