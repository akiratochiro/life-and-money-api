package com.akiratochiro.life_and_money_api.bill;

import org.junit.jupiter.api.Test;

import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScheduledBillTest {

    private static final YearMonth MARCH = YearMonth.of(2026, 3);

    @Test
    void aOneOffBillOccursOnlyInItsMonth() {
        // given
        ScheduledBill bill = new ScheduledBill(1L, BillRecurrence.ONCE, MARCH, null);

        // then
        assertThat(bill.occursIn(MARCH)).isTrue();
        assertThat(bill.occursIn(MARCH.minusMonths(1))).isFalse();
        assertThat(bill.occursIn(MARCH.plusMonths(1))).isFalse();
        assertThat(bill.occursIn(MARCH.plusYears(1))).isFalse();
    }

    @Test
    void aOneOffBillEndsInItsOwnMonthWhateverEndIsGiven() {
        ScheduledBill bill = new ScheduledBill(1L, BillRecurrence.ONCE, MARCH, MARCH.plusMonths(5));

        assertThat(bill.getEndMonth()).isEqualTo(MARCH);
    }

    @Test
    void aMonthlyBillWithoutEndOccursEveryMonthFromItsStart() {
        // given
        ScheduledBill bill = new ScheduledBill(1L, BillRecurrence.MONTHLY, MARCH, null);

        // then
        assertThat(bill.occursIn(MARCH.minusMonths(1))).isFalse();
        assertThat(bill.occursIn(MARCH)).isTrue();
        assertThat(bill.occursIn(MARCH.plusMonths(1))).isTrue();
        assertThat(bill.occursIn(MARCH.plusYears(10))).isTrue();
    }

    @Test
    void aMonthlyBillOccursInItsFirstAndLastMonthsAndNotAfter() {
        // given
        ScheduledBill bill = new ScheduledBill(1L, BillRecurrence.MONTHLY, MARCH, YearMonth.of(2026, 8));

        // then
        assertThat(bill.occursIn(MARCH)).isTrue();
        assertThat(bill.occursIn(YearMonth.of(2026, 8))).isTrue();
        assertThat(bill.occursIn(YearMonth.of(2026, 9))).isFalse();
    }

    @Test
    void aYearlyBillOccursOnlyInTheMonthItStarted() {
        // given: IPVA em março
        ScheduledBill bill = new ScheduledBill(1L, BillRecurrence.YEARLY, MARCH, null);

        // then
        assertThat(bill.occursIn(MARCH)).isTrue();
        assertThat(bill.occursIn(YearMonth.of(2027, 3))).isTrue();
        assertThat(bill.occursIn(YearMonth.of(2027, 2))).isFalse();
        assertThat(bill.occursIn(YearMonth.of(2026, 4))).isFalse();
        assertThat(bill.occursIn(YearMonth.of(2025, 3))).as("antes do início").isFalse();
    }

    @Test
    void aYearlyBillRespectsItsEnd() {
        ScheduledBill bill = new ScheduledBill(1L, BillRecurrence.YEARLY, MARCH, YearMonth.of(2027, 12));

        assertThat(bill.occursIn(YearMonth.of(2027, 3))).isTrue();
        assertThat(bill.occursIn(YearMonth.of(2028, 3))).isFalse();
    }

    @Test
    void anEndBeforeTheStartIsRejected() {
        assertThatThrownBy(() -> new ScheduledBill(1L, BillRecurrence.MONTHLY, MARCH, MARCH.minusMonths(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("before start month");
    }

    @Test
    void endingStopsTheOccurrencesAfterTheLastMonth() {
        // given
        ScheduledBill bill = new ScheduledBill(1L, BillRecurrence.MONTHLY, MARCH, null);

        // when
        bill.endAt(YearMonth.of(2026, 6));

        // then
        assertThat(bill.occursIn(YearMonth.of(2026, 6))).isTrue();
        assertThat(bill.occursIn(YearMonth.of(2026, 7))).isFalse();
    }

    @Test
    void endingCanBeUndoneWithNoEnd() {
        ScheduledBill bill = new ScheduledBill(1L, BillRecurrence.MONTHLY, MARCH, YearMonth.of(2026, 6));

        bill.endAt(null);

        assertThat(bill.getEndMonth()).isNull();
        assertThat(bill.occursIn(YearMonth.of(2030, 1))).isTrue();
    }

    @Test
    void endingBeforeTheStartIsRejected() {
        ScheduledBill bill = new ScheduledBill(1L, BillRecurrence.MONTHLY, MARCH, null);

        assertThatThrownBy(() -> bill.endAt(MARCH.minusMonths(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("before its start month");
    }
}
