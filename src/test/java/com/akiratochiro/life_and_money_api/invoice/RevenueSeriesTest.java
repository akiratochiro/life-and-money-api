package com.akiratochiro.life_and_money_api.invoice;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static com.akiratochiro.life_and_money_api.invoice.RevenueGranularity.DAY;
import static com.akiratochiro.life_and_money_api.invoice.RevenueGranularity.MONTH;
import static com.akiratochiro.life_and_money_api.invoice.RevenueGranularity.WEEK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RevenueSeriesTest {

    private static RevenueSeries.Entry entry(String date, String amount) {
        return new RevenueSeries.Entry(LocalDate.parse(date), new BigDecimal(amount));
    }

    private static List<RevenueSeries.Point> series(String from, String to, RevenueGranularity granularity,
                                                    RevenueSeries.Entry... entries) {
        RevenueSeries.Range range = RevenueSeries.range(LocalDate.parse(from), LocalDate.parse(to), granularity);
        return RevenueSeries.build(range, granularity, List.of(entries));
    }

    @Test
    void monthsWithoutNotesComeAsZero() {
        List<RevenueSeries.Point> points = series("2026-08-15", "2026-11-11", MONTH,
                entry("2026-08-31", "1000.00"), entry("2026-10-01", "500.00"), entry("2026-10-31", "250.00"));

        assertThat(points).extracting(RevenueSeries.Point::start).containsExactly(
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1));
        assertThat(points).extracting(p -> p.amount().toPlainString()).containsExactly("1000.00", "0", "750.00", "0");
        assertThat(points).extracting(RevenueSeries.Point::invoices).containsExactly(1, 0, 2, 0);
        assertThat(points.get(1).end()).isEqualTo(LocalDate.of(2026, 9, 30));
    }

    /** A semana é ISO: começa na segunda. 29/12/2025 é segunda e a semana vai até 04/01/2026. */
    @Test
    void aWeekThatCrossesTheYearAppearsWholeUnderItsMonday() {
        List<RevenueSeries.Point> points = series("2026-01-01", "2026-01-11", WEEK,
                entry("2025-12-30", "100.00"), entry("2026-01-04", "50.00"), entry("2026-01-05", "10.00"));

        assertThat(points).extracting(RevenueSeries.Point::start)
                .containsExactly(LocalDate.of(2025, 12, 29), LocalDate.of(2026, 1, 5));
        assertThat(points.get(0).end()).isEqualTo(LocalDate.of(2026, 1, 4));
        assertThat(points.get(0).amount()).isEqualByComparingTo("150.00");
        assertThat(points.get(1).amount()).isEqualByComparingTo("10.00");
    }

    @Test
    void theRangeIsWidenedToWholePeriods() {
        assertThat(RevenueSeries.range(LocalDate.of(2026, 11, 11), LocalDate.of(2026, 11, 11), WEEK))
                .isEqualTo(new RevenueSeries.Range(LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 15)));
        assertThat(RevenueSeries.range(LocalDate.of(2026, 2, 10), LocalDate.of(2026, 2, 12), MONTH))
                .isEqualTo(new RevenueSeries.Range(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28)));
    }

    @Test
    void everyDayGetsAPoint() {
        List<RevenueSeries.Point> points = series("2026-11-09", "2026-11-11", DAY, entry("2026-11-10", "42.00"));

        assertThat(points).extracting(p -> p.amount().toPlainString()).containsExactly("0", "42.00", "0");
    }

    @Test
    void entriesOutsideTheRangeAreIgnored() {
        assertThat(series("2026-11-01", "2026-11-30", MONTH, entry("2026-12-01", "99.00")))
                .singleElement().extracting(RevenueSeries.Point::invoices).isEqualTo(0);
    }

    @Test
    void eachGranularityHasALimit() {
        assertThat(RevenueSeries.range(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 4, 3), DAY)).isNotNull(); // 93 dias
        assertThatThrownBy(() -> RevenueSeries.range(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 4, 4), DAY))
                .isInstanceOf(InvalidRevenueRangeException.class)
                .hasMessage("Escolha um período de no máximo 93 dias.");
        assertThatThrownBy(() -> RevenueSeries.range(LocalDate.of(2025, 1, 1), LocalDate.of(2026, 3, 1), WEEK))
                .hasMessage("Escolha um período de no máximo 53 semanas.");
        assertThat(RevenueSeries.range(LocalDate.of(2024, 1, 1), LocalDate.of(2026, 12, 31), MONTH)).isNotNull(); // 36 meses
        assertThatThrownBy(() -> RevenueSeries.range(LocalDate.of(2024, 1, 1), LocalDate.of(2027, 1, 1), MONTH))
                .hasMessage("Escolha um período de no máximo 36 meses.");
    }

    @Test
    void theStartMustComeBeforeTheEnd() {
        assertThatThrownBy(() -> RevenueSeries.range(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1), MONTH))
                .hasMessage("A data inicial precisa ser antes da final.");
    }
}
