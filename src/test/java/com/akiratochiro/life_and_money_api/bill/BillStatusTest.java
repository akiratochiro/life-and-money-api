package com.akiratochiro.life_and_money_api.bill;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class BillStatusTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 31);

    @Test
    void dueTodayIsStillPending() {
        assertThat(BillStatus.of(false, TODAY, TODAY)).isEqualTo(BillStatus.PENDING);
    }

    @Test
    void dueYesterdayIsOverdue() {
        assertThat(BillStatus.of(false, TODAY.minusDays(1), TODAY)).isEqualTo(BillStatus.OVERDUE);
    }

    @Test
    void dueInTheFutureIsPending() {
        assertThat(BillStatus.of(false, TODAY.plusDays(1), TODAY)).isEqualTo(BillStatus.PENDING);
    }

    @Test
    void paidWinsOverEverything() {
        assertThat(BillStatus.of(true, TODAY.minusMonths(3), TODAY)).isEqualTo(BillStatus.PAID);
        assertThat(BillStatus.of(true, TODAY.plusDays(10), TODAY)).isEqualTo(BillStatus.PAID);
    }
}
