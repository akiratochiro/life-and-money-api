package com.akiratochiro.life_and_money_api.taxengine;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProfitDistributionTest {

    private static final TaxTable T = Tables2026.table();

    private static BigDecimal d(String value) {
        return new BigDecimal(value);
    }

    /** Até R$ 50.000 no mês, nada; R$ 60.000 → 10% sobre o total = 6.000,00, não sobre os 10.000 excedentes. */
    @Test
    void theWithholdingIsOnTheWholeMonthAboveTheLimit() {
        assertThat(ProfitDistribution.suggestedWithholding(d("50000.00"), T)).isEqualByComparingTo("0");
        assertThat(ProfitDistribution.suggestedWithholding(d("60000.00"), T)).isEqualByComparingTo("6000.00");
        assertThat(ProfitDistribution.suggestedWithholding(d("50000.01"), T)).isEqualByComparingTo("5000.00");
    }

    /** Anexo III, faixa 1: 10.000 × 6% × 4% (fração do IRPJ) = 24,00. */
    @Test
    void theIrpjInsideTheDas() {
        assertThat(ProfitDistribution.irpjInDas(d("10000"), d("0.06"), d("0.04"))).isEqualByComparingTo("24.00");
    }

    /**
     * Dois meses de 10.000 no Simples (serviços, 32%) com IRPJ de 24,00 cada: 2 × (3.200 − 24) = 6.352,00.
     * Um MEI de comércio com 5.000: 5.000 × 8% = 400,00, sem IRPJ.
     */
    @Test
    void theExemptionLimitIsThePresumptionMinusTheIrpj() {
        assertThat(ProfitDistribution.exemptionLimit(List.of(
                new ProfitDistribution.Month(d("10000"), d("0.32"), d("24.00")),
                new ProfitDistribution.Month(d("10000"), d("0.32"), d("24.00"))))).isEqualByComparingTo("6352.00");
        assertThat(ProfitDistribution.exemptionLimit(List.of(
                new ProfitDistribution.Month(d("5000"), d("0.08"), BigDecimal.ZERO)))).isEqualByComparingTo("400.00");
        assertThat(ProfitDistribution.exemptionLimit(List.of())).isEqualByComparingTo("0");
    }
}
