package com.akiratochiro.life_and_money_api.companyexpense;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ExpenseCalendarTest {

    private static RecurringExpense expense(long id, String start, String end) {
        RecurringExpense expense = new RecurringExpense(1L, 1L, YearMonth.parse(start), end == null ? null : YearMonth.parse(end));
        ReflectionTestUtils.setField(expense, "id", id);
        return expense;
    }

    private static RecurringExpenseTerms terms(long expenseId, String amount, int day, String from, String to) {
        RecurringExpenseTerms terms = new RecurringExpenseTerms(1L, expenseId, 5L, "Aluguel", new BigDecimal(amount), day, YearMonth.parse(from));
        if (to != null) {
            terms.close(YearMonth.parse(to));
        }
        return terms;
    }

    @Test
    void dayThirtyOneFallsOnTheLastDayOfShortMonths() {
        RecurringExpense rent = expense(1, "2026-01", null);
        var termsByExpense = Map.of(1L, List.of(terms(1, "2000", 31, "2026-01", null)));

        assertThat(ExpenseCalendar.in(YearMonth.of(2026, 2), List.of(rent), termsByExpense).getFirst().dueDate())
                .isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(ExpenseCalendar.in(YearMonth.of(2028, 2), List.of(rent), termsByExpense).getFirst().dueDate())
                .isEqualTo(LocalDate.of(2028, 2, 29));
    }

    @Test
    void eachMonthUsesTheTermsValidAtTheTime() {
        RecurringExpense rent = expense(1, "2026-01", null);
        var termsByExpense = Map.of(1L, List.of(terms(1, "2000", 5, "2026-01", "2026-06"), terms(1, "2300", 10, "2026-07", null)));

        assertThat(ExpenseCalendar.in(YearMonth.of(2026, 6), List.of(rent), termsByExpense).getFirst().terms().getAmount())
                .isEqualByComparingTo("2000");
        assertThat(ExpenseCalendar.in(YearMonth.of(2026, 7), List.of(rent), termsByExpense).getFirst().dueDate())
                .isEqualTo(LocalDate.of(2026, 7, 10));
    }

    @Test
    void noOccurrenceBeforeTheStartOrAfterTheEnd() {
        RecurringExpense rent = expense(1, "2026-03", "2026-05");
        var termsByExpense = Map.of(1L, List.of(terms(1, "2000", 5, "2026-03", null)));

        assertThat(ExpenseCalendar.in(YearMonth.of(2026, 2), List.of(rent), termsByExpense)).isEmpty();
        assertThat(ExpenseCalendar.in(YearMonth.of(2026, 5), List.of(rent), termsByExpense)).hasSize(1);
        assertThat(ExpenseCalendar.in(YearMonth.of(2026, 6), List.of(rent), termsByExpense)).isEmpty();
    }

    /** Atrasada pelo dia do calendário: no próprio vencimento ainda é pendente. */
    @Test
    void statusIsPaidPendingOrOverdueByTheCalendarDay() {
        LocalDate due = LocalDate.of(2026, 11, 10);

        assertThat(ExpenseCalendar.status(due, true, LocalDate.of(2026, 12, 1))).isEqualTo(ExpenseStatus.PAID);
        assertThat(ExpenseCalendar.status(due, false, LocalDate.of(2026, 11, 10))).isEqualTo(ExpenseStatus.PENDING);
        assertThat(ExpenseCalendar.status(due, false, LocalDate.of(2026, 11, 11))).isEqualTo(ExpenseStatus.OVERDUE);
    }
}
