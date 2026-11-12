package com.akiratochiro.life_and_money_api.taxengine;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Os exemplos feitos à mão do plano da Fase 8: a competência em jogo é 12/2026, a janela vai até 11/2026. */
class FatorRProjectionTest {

    private static final TaxTable T = Tables2026.table();
    private static final YearMonth DECEMBER = YearMonth.of(2026, 12);
    private static final YearMonth LONG_AGO = YearMonth.of(2024, 1);

    private static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    /** O mesmo valor em cada mês, de {@code from} a 11/2026. */
    private static Map<YearMonth, BigDecimal> monthly(String amount, YearMonth from) {
        Map<YearMonth, BigDecimal> months = new HashMap<>();
        for (YearMonth m = from; !m.isAfter(YearMonth.of(2026, 11)); m = m.plusMonths(1)) {
            months.put(m, d(amount));
        }
        return months;
    }

    private static FatorRProjection.Result project(String revenue, String payroll, String target, String proLabore) {
        return FatorRProjection.project(DECEMBER, LONG_AGO, monthly(payroll, LONG_AGO), monthly(revenue, LONG_AGO),
                d(target), d(proLabore), d(revenue), T);
    }

    @Test
    void atOrAboveTheTargetThereIsNothingToSuggest() {
        // 36.000 ÷ 120.000 = 30%
        var result = project("10000", "3000", "0.28", "3000");

        assertThat(result.fatorR().ratio()).isEqualByComparingTo("0.30");
        assertThat(result.fatorR().window().from()).isEqualTo(YearMonth.of(2025, 12));
        assertThat(result.status()).isEqualTo(FatorRProjection.Status.OK);
        assertThat(result.suggestion()).isNull();
    }

    /**
     * Receita de 100.000 e folha de 27.500 por mês: 330.000 ÷ 1.200.000 = 27,5%, Anexo V.
     * Falta 28% × 1.200.000 − 330.000 = 6.000: o pró-labore vai de 27.500 para 33.500.
     * RBT12 1.200.000 (faixa 4): III = (192.000 − 35.640) ÷ 1,2 mi = 13,03% → 13.030,00;
     * V = (246.000 − 17.100) ÷ 1,2 mi = 19,075% → 19.075,00. O INSS já está no teto nos dois; o IRRF a
     * mais é 27,5% × 6.000 = 1.650,00. Economia = 6.045 − 1.650 = 4.395,00.
     */
    @Test
    void belowTheThresholdSuggestsTheProLaboreAndTheSavings() {
        var result = project("100000", "27500", "0.28", "27500");

        assertThat(result.status()).isEqualTo(FatorRProjection.Status.BELOW_THRESHOLD);
        assertThat(result.fatorR().ratio()).isEqualByComparingTo("0.275");
        var s = result.suggestion();
        assertThat(s.missingPayroll()).isEqualByComparingTo("6000.00");
        assertThat(s.currentProLabore()).isEqualByComparingTo("27500");
        assertThat(s.suggestedProLabore()).isEqualByComparingTo("33500.00");
        assertThat(s.dasAnnexIII()).isEqualByComparingTo("13030.00");
        assertThat(s.dasAnnexV()).isEqualByComparingTo("19075.00");
        assertThat(s.extraProLaboreTaxes()).isEqualByComparingTo("1650.00");
        assertThat(s.savings()).isEqualByComparingTo("4395.00");
    }

    /**
     * Receita de 10.000 e folha de 2.000 por mês: 20%. Falta 33.600 − 24.000 = 9.600; o pró-labore iria
     * de 2.000 para 11.600. O DAS cai só de 1.550 (V, 15,5%) para 600 (III, 6%), e o INSS e o IRRF a mais
     * passam disso: a economia é negativa, e o Anexo V sai mais barato.
     */
    @Test
    void theSavingsMayBeNegative() {
        var result = project("10000", "2000", "0.28", "2000");

        var s = result.suggestion();
        assertThat(s.missingPayroll()).isEqualByComparingTo("9600.00");
        assertThat(s.suggestedProLabore()).isEqualByComparingTo("11600.00");
        assertThat(s.dasAnnexIII()).isEqualByComparingTo("600.00");
        assertThat(s.dasAnnexV()).isEqualByComparingTo("1550.00");
        BigDecimal extra = ProLaboreCalculator.of(d("11600"), T).withheld().subtract(ProLaboreCalculator.of(d("2000"), T).withheld());
        assertThat(s.extraProLaboreTaxes()).isEqualByComparingTo(extra);
        assertThat(s.savings()).isEqualByComparingTo(d("950").subtract(extra)).isNegative();
    }

    /** Meta de 30% e Fator R de 29% (34.800 ÷ 120.000): ainda Anexo III, falta 1.200 para a meta, sem economia. */
    @Test
    void betweenTheThresholdAndTheTargetOnlyTheMarginIsMissing() {
        var result = project("10000", "2900", "0.30", "2900");

        assertThat(result.status()).isEqualTo(FatorRProjection.Status.BELOW_TARGET);
        assertThat(result.target()).isEqualByComparingTo("0.30");
        assertThat(result.suggestion().missingPayroll()).isEqualByComparingTo("1200.00");
        assertThat(result.suggestion().suggestedProLabore()).isEqualByComparingTo("4100.00");
        assertThat(result.suggestion().savings()).isNull();
    }

    /** Uma meta abaixo de 28% vale como 28%: a lei não muda pela meta. */
    @Test
    void theTargetIsNeverBelowTheLegalThreshold() {
        var result = project("10000", "2700", "0.20", "2700");

        assertThat(result.target()).isEqualByComparingTo("0.28");
        assertThat(result.status()).isEqualTo(FatorRProjection.Status.BELOW_THRESHOLD);
    }

    /**
     * Aberta em 09/2026: a janela de dezembro é 09 a 11 (proporcional). 6.000 ÷ 30.000 = 20%; falta
     * 8.400 − 6.000 = 2.400. O RBT12 é 30.000 × 12 ÷ 3 = 120.000 (faixa 1).
     */
    @Test
    void aYoungCompanyUsesTheProportionalWindow() {
        YearMonth start = YearMonth.of(2026, 9);
        var result = FatorRProjection.project(DECEMBER, start, monthly("2000", start), monthly("10000", start),
                d("0.28"), d("2000"), d("10000"), T);

        assertThat(result.fatorR().window().method()).isEqualTo(Rbt12.Method.PROPORTIONAL);
        assertThat(result.suggestion().missingPayroll()).isEqualByComparingTo("2400.00");
        assertThat(result.suggestion().suggestedProLabore()).isEqualByComparingTo("4400.00");
        assertThat(result.suggestion().dasAnnexIII()).isEqualByComparingTo("600.00");
    }

    /** Sem pró-labore, a sugestão nunca fica abaixo do salário mínimo. */
    @Test
    void theSuggestionIsNeverBelowTheMinimumWage() {
        Map<YearMonth, BigDecimal> payroll = monthly("2780", LONG_AGO);
        payroll.put(YearMonth.of(2026, 11), d("2600")); // 33.180 ÷ 120.000 = 27,65%: falta 420
        var result = FatorRProjection.project(DECEMBER, LONG_AGO, payroll, monthly("10000", LONG_AGO),
                d("0.28"), BigDecimal.ZERO, d("10000"), T);

        assertThat(result.suggestion().missingPayroll()).isEqualByComparingTo("420.00");
        assertThat(result.suggestion().suggestedProLabore()).isEqualByComparingTo("1621.00");
    }

    @Test
    void withoutRevenueThereIsNothingToCorrect() {
        var result = FatorRProjection.project(DECEMBER, LONG_AGO, Map.of(), Map.of(), d("0.28"), BigDecimal.ZERO,
                BigDecimal.ZERO, T);

        assertThat(result.status()).isEqualTo(FatorRProjection.Status.NO_REVENUE);
        assertThat(result.suggestion()).isNull();
    }
}
