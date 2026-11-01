package com.akiratochiro.life_and_money_api.bill;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BillCalendarTest {

    private long nextId = 1;

    private ScheduledBill bill(BillRecurrence recurrence, String start, String end) {
        ScheduledBill bill = new ScheduledBill(1L, recurrence, YearMonth.parse(start),
                end == null ? null : YearMonth.parse(end));
        ReflectionTestUtils.setField(bill, "id", nextId++);
        return bill;
    }

    private static BillTerms terms(ScheduledBill bill, String name, String amount, int dueDay, String validFrom) {
        return new BillTerms(1L, bill.getId(), 10L, name, new BigDecimal(amount), dueDay, YearMonth.parse(validFrom));
    }

    private static List<LocalDate> dueDates(List<BillCalendar.Occurrence> occurrences) {
        return occurrences.stream().map(BillCalendar.Occurrence::dueDate).toList();
    }

    @Test
    void aBillDueOnThe31stFallsOnTheLastDayOfShortMonths() {
        // given
        ScheduledBill rent = bill(BillRecurrence.MONTHLY, "2026-01", null);
        Map<Long, List<BillTerms>> terms = Map.of(rent.getId(), List.of(terms(rent, "Aluguel", "1500", 31, "2026-01")));

        // when: janeiro a junho de 2026
        List<BillCalendar.Occurrence> occurrences = BillCalendar.between(List.of(rent), terms,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30));

        // then: uma por mês, e nenhuma escorrega para o mês seguinte
        assertThat(dueDates(occurrences)).containsExactly(
                LocalDate.of(2026, 1, 31),
                LocalDate.of(2026, 2, 28),
                LocalDate.of(2026, 3, 31),
                LocalDate.of(2026, 4, 30),
                LocalDate.of(2026, 5, 31),
                LocalDate.of(2026, 6, 30));
    }

    @Test
    void inALeapYearFebruaryEndsOnThe29th() {
        ScheduledBill rent = bill(BillRecurrence.MONTHLY, "2028-01", null);
        Map<Long, List<BillTerms>> terms = Map.of(rent.getId(), List.of(terms(rent, "Aluguel", "1500", 31, "2028-01")));

        List<BillCalendar.Occurrence> occurrences = BillCalendar.between(List.of(rent), terms,
                LocalDate.of(2028, 2, 1), LocalDate.of(2028, 2, 29));

        assertThat(dueDates(occurrences)).containsExactly(LocalDate.of(2028, 2, 29));
    }

    @Test
    void aYearlyBillOnFebruary29FallsOnThe28thInCommonYears() {
        // given: começou em fevereiro de 2028, bissexto, com vencimento no dia 29
        ScheduledBill fee = bill(BillRecurrence.YEARLY, "2028-02", null);
        Map<Long, List<BillTerms>> terms = Map.of(fee.getId(), List.of(terms(fee, "Anuidade", "400", 29, "2028-02")));

        // when
        List<BillCalendar.Occurrence> occurrences = BillCalendar.between(List.of(fee), terms,
                LocalDate.of(2028, 1, 1), LocalDate.of(2032, 12, 31));

        // then
        assertThat(dueDates(occurrences)).containsExactly(
                LocalDate.of(2028, 2, 29),
                LocalDate.of(2029, 2, 28),
                LocalDate.of(2030, 2, 28),
                LocalDate.of(2031, 2, 28),
                LocalDate.of(2032, 2, 29));
    }

    @Test
    void aWindowCrossingTheYearCatchesBothSides() {
        // given: uma conta no dia 30 e outra no dia 2
        ScheduledBill card = bill(BillRecurrence.MONTHLY, "2026-01", null);
        ScheduledBill school = bill(BillRecurrence.MONTHLY, "2026-01", null);
        Map<Long, List<BillTerms>> terms = Map.of(
                card.getId(), List.of(terms(card, "Cartão", "900", 30, "2026-01")),
                school.getId(), List.of(terms(school, "Escola", "700", 2, "2026-01")));

        // when: 28/12 a 03/01
        List<BillCalendar.Occurrence> occurrences = BillCalendar.between(List.of(school, card), terms,
                LocalDate.of(2026, 12, 28), LocalDate.of(2027, 1, 3));

        // then
        assertThat(occurrences).extracting(BillCalendar.Occurrence::month)
                .containsExactly(YearMonth.of(2026, 12), YearMonth.of(2027, 1));
        assertThat(dueDates(occurrences)).containsExactly(LocalDate.of(2026, 12, 30), LocalDate.of(2027, 1, 2));
    }

    @Test
    void dueDatesOutsideTheWindowAreLeftOut() {
        ScheduledBill rent = bill(BillRecurrence.MONTHLY, "2026-01", null);
        Map<Long, List<BillTerms>> terms = Map.of(rent.getId(), List.of(terms(rent, "Aluguel", "1500", 5, "2026-01")));

        List<BillCalendar.Occurrence> occurrences = BillCalendar.between(List.of(rent), terms,
                LocalDate.of(2026, 3, 6), LocalDate.of(2026, 4, 4));

        assertThat(occurrences).isEmpty();
    }

    @Test
    void eachMonthUsesTheTermsThatWereValidThen() {
        // given: R$ 180 até outubro, R$ 210 a partir de novembro
        ScheduledBill power = bill(BillRecurrence.MONTHLY, "2026-08", null);
        BillTerms old = terms(power, "Luz", "180.00", 10, "2026-08");
        old.close(YearMonth.of(2026, 10));
        BillTerms current = terms(power, "Luz", "210.00", 12, "2026-11");

        // when
        List<BillCalendar.Occurrence> occurrences = BillCalendar.between(List.of(power),
                Map.of(power.getId(), List.of(old, current)),
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 12, 31));

        // then
        assertThat(occurrences).extracting(occurrence -> occurrence.terms().getAmount().toPlainString())
                .containsExactly("180.00", "180.00", "210.00", "210.00");
        assertThat(dueDates(occurrences)).containsExactly(
                LocalDate.of(2026, 9, 10), LocalDate.of(2026, 10, 10),
                LocalDate.of(2026, 11, 12), LocalDate.of(2026, 12, 12));
    }

    @Test
    void sameDayOccurrencesAreOrderedByName() {
        ScheduledBill water = bill(BillRecurrence.MONTHLY, "2026-01", null);
        ScheduledBill internet = bill(BillRecurrence.MONTHLY, "2026-01", null);
        Map<Long, List<BillTerms>> terms = Map.of(
                water.getId(), List.of(terms(water, "Água", "90", 10, "2026-01")),
                internet.getId(), List.of(terms(internet, "internet", "100", 10, "2026-01")));

        List<BillCalendar.Occurrence> occurrences = BillCalendar.between(List.of(internet, water), terms,
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31));

        assertThat(occurrences).extracting(occurrence -> occurrence.terms().getName())
                .containsExactly("Água", "internet");
    }

    @Test
    void aOneOffBillShowsUpOnlyInItsMonth() {
        ScheduledBill repair = bill(BillRecurrence.ONCE, "2026-07", null);
        Map<Long, List<BillTerms>> terms = Map.of(repair.getId(), List.of(terms(repair, "Mecânico", "800", 20, "2026-07")));

        List<BillCalendar.Occurrence> occurrences = BillCalendar.between(List.of(repair), terms,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

        assertThat(dueDates(occurrences)).containsExactly(LocalDate.of(2026, 7, 20));
    }
}
