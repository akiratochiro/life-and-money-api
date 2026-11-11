package com.akiratochiro.life_and_money_api.taxengine;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SimeiDueDatesAdvisorTest {

    private static final TaxTable T = Tables2026.table();

    @Test
    void theDasMeiOf2026() {
        assertThat(SimeiCalculator.das(SimeiCalculator.Activity.SERVICES, T).amount()).isEqualByComparingTo("86.05");
        assertThat(SimeiCalculator.das(SimeiCalculator.Activity.TRADE_OR_INDUSTRY, T).amount()).isEqualByComparingTo("82.05");
        assertThat(SimeiCalculator.das(SimeiCalculator.Activity.BOTH, T).amount()).isEqualByComparingTo("87.05");
        assertThat(SimeiCalculator.das(SimeiCalculator.Activity.BOTH, T).inss()).isEqualByComparingTo("81.05");
    }

    /** Fins de semana e um feriado, como o HolidayCalendar faria. */
    private static final Set<LocalDate> HOLIDAYS = Set.of(LocalDate.of(2026, 11, 20));
    private static final BusinessDays DAYS = date -> date.getDayOfWeek() != DayOfWeek.SATURDAY
            && date.getDayOfWeek() != DayOfWeek.SUNDAY && !HOLIDAYS.contains(date);

    @Test
    void theDasIsPostponedAndTheDarfIsAnticipated() {
        // Competência de outubro de 2026: o dia 20/11 é feriado (Consciência Negra), uma sexta.
        assertThat(DueDates.das(YearMonth.of(2026, 10), DAYS)).isEqualTo(LocalDate.of(2026, 11, 23));
        assertThat(DueDates.darf(YearMonth.of(2026, 10), DAYS)).isEqualTo(LocalDate.of(2026, 11, 19));
        // Competência de maio de 2026: 20/06 é sábado.
        assertThat(DueDates.das(YearMonth.of(2026, 5), DAYS)).isEqualTo(LocalDate.of(2026, 6, 22));
        assertThat(DueDates.darf(YearMonth.of(2026, 5), DAYS)).isEqualTo(LocalDate.of(2026, 6, 19));
    }

    @Test
    void theAnnualDeclarations() {
        assertThat(DueDates.defis(2026)).isEqualTo(LocalDate.of(2027, 3, 31));
        assertThat(DueDates.dasnSimei(2026)).isEqualTo(LocalDate.of(2027, 5, 31));
    }

    /**
     * A comparação do plano: receita de 25.000 por mês. Anexo III com pró-labore de 7.000: DAS 2.020,00 +
     * INSS 770,00 + IRRF 757,91 = 3.547,91. Anexo V com o mínimo (1.621): DAS 4.125,00 + INSS 178,31 = 4.303,31.
     */
    @Test
    void theAdvisorComparesAnnexIIIWithTheNeededProLaboreAndAnnexVWithTheMinimum() {
        var result = ProLaboreAdvisor.advise(new BigDecimal("25000"), BigDecimal.ZERO, new BigDecimal("0.28"), T);

        assertThat(result.annexIII().proLabore()).isEqualByComparingTo("7000.00");
        assertThat(result.annexIII().totalCost()).isEqualByComparingTo("3547.91");
        assertThat(result.annexV().proLabore()).isEqualByComparingTo("1621.00");
        assertThat(result.annexV().totalCost()).isEqualByComparingTo("4303.31");
        assertThat(result.savings()).isEqualByComparingTo("755.40");
        assertThat(result.cheaper()).isEqualTo(Annex.III);
    }

    @Test
    void theAdvisorNeverGoesBelowTheLegalThresholdNorTheMinimumWage() {
        // Meta da empresa abaixo do limite legal: vale o limite. Faturamento pequeno: vale o salário mínimo.
        var low = ProLaboreAdvisor.advise(new BigDecimal("3000"), BigDecimal.ZERO, new BigDecimal("0.20"), T);
        assertThat(low.target()).isEqualByComparingTo("0.28");
        assertThat(low.annexIII().proLabore()).isEqualByComparingTo("1621.00");

        // Meta de 30% e salários de 2.000 já na folha: 30% × 25.000 − 2.000 = 5.500.
        var withPayroll = ProLaboreAdvisor.advise(new BigDecimal("25000"), new BigDecimal("2000"), new BigDecimal("0.30"), T);
        assertThat(withPayroll.annexIII().proLabore()).isEqualByComparingTo("5500.00");
    }

    @Test
    void competencesFrom2027AreNotYetSupported() {
        TaxRules.requireSupported(YearMonth.of(2026, 12));
        assertThatThrownBy(() -> TaxRules.requireSupported(YearMonth.of(2027, 1)))
                .isInstanceOf(TaxCalculationException.class)
                .hasMessageContaining("mudam em janeiro de 2027");
    }
}
