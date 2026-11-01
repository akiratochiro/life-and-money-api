package com.akiratochiro.life_and_money_api.bill;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BillTermsTest {

    private static BillTerms termsDueOn(int dueDay, YearMonth validFrom) {
        return new BillTerms(1L, 5L, 10L, "Luz", new BigDecimal("180.00"), dueDay, validFrom);
    }

    @ParameterizedTest(name = "dia {0} em {1} vence em {2}")
    @CsvSource({
            "31, 2026-02, 2026-02-28",   // fevereiro comum
            "31, 2028-02, 2028-02-29",   // fevereiro bissexto
            "30, 2026-02, 2026-02-28",
            "29, 2026-02, 2026-02-28",
            "29, 2028-02, 2028-02-29",
            "31, 2026-04, 2026-04-30",   // meses de 30 dias
            "31, 2026-06, 2026-06-30",
            "31, 2026-09, 2026-09-30",
            "31, 2026-11, 2026-11-30",
            "31, 2026-01, 2026-01-31",   // mês de 31 dias não muda
            "30, 2026-04, 2026-04-30",
            "1,  2026-02, 2026-02-01",
            "28, 2026-02, 2026-02-28"
    })
    void aDayTheMonthDoesNotHaveFallsOnItsLastDay(int dueDay, String month, String expected) {
        // given
        BillTerms terms = termsDueOn(dueDay, YearMonth.of(2026, 1));

        // when
        LocalDate dueDate = terms.dueDateIn(YearMonth.parse(month));

        // then
        assertThat(dueDate).isEqualTo(LocalDate.parse(expected));
    }

    @Test
    void theDueDateNeverSpillsIntoTheNextMonth() {
        // given
        BillTerms terms = termsDueOn(31, YearMonth.of(2026, 1));

        // when / then: em cada mês de dois anos, o vencimento fica dentro do próprio mês
        for (YearMonth month = YearMonth.of(2027, 1); month.isBefore(YearMonth.of(2029, 1)); month = month.plusMonths(1)) {
            assertThat(YearMonth.from(terms.dueDateIn(month))).isEqualTo(month);
        }
    }

    @ParameterizedTest
    @CsvSource({"0", "32", "-1"})
    void aDueDayOutsideOneToThirtyOneIsRejected(int dueDay) {
        assertThatThrownBy(() -> termsDueOn(dueDay, YearMonth.of(2026, 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 1 and 31");
    }

    @Test
    void openTermsAreValidFromTheirStartOnwards() {
        // given
        BillTerms terms = termsDueOn(10, YearMonth.of(2026, 3));

        // then
        assertThat(terms.isValidIn(YearMonth.of(2026, 2))).isFalse();
        assertThat(terms.isValidIn(YearMonth.of(2026, 3))).isTrue();
        assertThat(terms.isValidIn(YearMonth.of(2030, 12))).isTrue();
    }

    @Test
    void closedTermsStopBeingValidAfterTheirLastMonth() {
        // given
        BillTerms terms = termsDueOn(10, YearMonth.of(2026, 3));

        // when
        terms.close(YearMonth.of(2026, 8));

        // then
        assertThat(terms.isActive()).isFalse();
        assertThat(terms.isValidIn(YearMonth.of(2026, 8))).isTrue();
        assertThat(terms.isValidIn(YearMonth.of(2026, 9))).isFalse();
    }

    @Test
    void closingBeforeTheStartIsRejected() {
        // given
        BillTerms terms = termsDueOn(10, YearMonth.of(2026, 3));

        // when / then
        assertThatThrownBy(() -> terms.close(YearMonth.of(2026, 2)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("before their start month");
    }

    @Test
    void closingTwiceIsRejected() {
        // given
        BillTerms terms = termsDueOn(10, YearMonth.of(2026, 3));
        terms.close(YearMonth.of(2026, 5));

        // when / then
        assertThatThrownBy(() -> terms.close(YearMonth.of(2026, 6)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already closed");
    }

    @Test
    void theNameIsStoredWithoutSurroundingSpaces() {
        BillTerms terms = new BillTerms(1L, 5L, 10L, "  Aluguel  ", new BigDecimal("1500.00"), 5, YearMonth.of(2026, 1));

        assertThat(terms.getName()).isEqualTo("Aluguel");
    }
}
