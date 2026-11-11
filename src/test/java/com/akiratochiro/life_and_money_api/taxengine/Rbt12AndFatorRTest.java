package com.akiratochiro.life_and_money_api.taxengine;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class Rbt12AndFatorRTest {

    private static final TaxTable T = Tables2026.table();
    private static final YearMonth MARCH = YearMonth.of(2026, 3);

    private static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    @Test
    void inTheFirstMonthTheRbt12IsTheMonthTimesTwelve() {
        var result = Rbt12.of(MARCH, MARCH, Map.of(MARCH, d("10000")));

        assertThat(result.window().method()).isEqualTo(Rbt12.Method.FIRST_MONTH);
        assertThat(result.value()).isEqualByComparingTo("120000.00");
    }

    @Test
    void untilTheTwelfthMonthItIsTheAverageOfThePreviousMonthsTimesTwelve() {
        // Abril: média de março (10.000) × 12. Maio: média de março e abril ((10.000 + 14.000) ÷ 2) × 12.
        Map<YearMonth, BigDecimal> revenue = Map.of(MARCH, d("10000"), MARCH.plusMonths(1), d("14000"));

        assertThat(Rbt12.of(MARCH.plusMonths(1), MARCH, revenue).value()).isEqualByComparingTo("120000.00");
        assertThat(Rbt12.of(MARCH.plusMonths(2), MARCH, revenue).value()).isEqualByComparingTo("144000.00");
        assertThat(Rbt12.of(MARCH.plusMonths(2), MARCH, revenue).window().method()).isEqualTo(Rbt12.Method.PROPORTIONAL);
    }

    @Test
    void fromTheThirteenthMonthItIsTheSumOfThePreviousTwelve() {
        YearMonth start = YearMonth.of(2024, 3);
        Map<YearMonth, BigDecimal> revenue = new java.util.HashMap<>();
        for (int i = 0; i < 40; i++) {
            revenue.put(start.plusMonths(i), d("1000"));
        }
        revenue.put(YearMonth.of(2026, 10), d("5000")); // a própria competência não entra

        var result = Rbt12.of(YearMonth.of(2026, 10), start, revenue);

        assertThat(result.window()).isEqualTo(new Rbt12.Window(YearMonth.of(2025, 10), YearMonth.of(2026, 9), Rbt12.Method.TWELVE_MONTHS));
        assertThat(result.value()).isEqualByComparingTo("12000.00");
    }

    @Test
    void fatorRAtOrAboveTwentyEightPercentIsAnnexIII() {
        YearMonth start = YearMonth.of(2024, 1);
        Map<YearMonth, BigDecimal> revenue = new java.util.HashMap<>();
        Map<YearMonth, BigDecimal> payroll = new java.util.HashMap<>();
        for (int i = 0; i < 36; i++) {
            revenue.put(start.plusMonths(i), d("25000"));
            payroll.put(start.plusMonths(i), d("7000"));
        }

        var exactly = FatorR.of(YearMonth.of(2026, 11), start, payroll, revenue, T);
        assertThat(exactly.ratio()).isEqualByComparingTo("0.28");
        assertThat(exactly.annex()).isEqualTo(Annex.III);

        payroll.replaceAll((month, value) -> d("6999.99"));
        assertThat(FatorR.of(YearMonth.of(2026, 11), start, payroll, revenue, T).annex()).isEqualTo(Annex.V);
    }

    @Test
    void withoutRevenueOrWithoutPayrollTheFixedValuesApply() {
        assertThat(FatorR.ratio(d("1621"), BigDecimal.ZERO)).isEqualByComparingTo("0.28");
        assertThat(FatorR.ratio(BigDecimal.ZERO, d("10000"))).isEqualByComparingTo("0.01");
        assertThat(FatorR.ratio(BigDecimal.ZERO, BigDecimal.ZERO)).isEqualByComparingTo("0.01");
    }
}
