package com.akiratochiro.life_and_money_api.companyexpense;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Expande as despesas fixas num mês. Os meses não existem no banco: são calculados a partir da fixa
 * e dos termos que valiam no mês, como no {@code BillCalendar} das contas pessoais.
 */
final class ExpenseCalendar {

    record Occurrence(RecurringExpense expense, RecurringExpenseTerms terms, YearMonth month, LocalDate dueDate) {
    }

    private ExpenseCalendar() {
    }

    static List<Occurrence> in(YearMonth month, List<RecurringExpense> expenses,
                               Map<Long, List<RecurringExpenseTerms>> termsByExpense) {
        List<Occurrence> occurrences = new ArrayList<>();
        for (RecurringExpense expense : expenses) {
            if (!expense.occursIn(month)) {
                continue;
            }
            RecurringExpenseTerms terms = termsIn(termsByExpense.getOrDefault(expense.getId(), List.of()), month);
            if (terms != null) {
                occurrences.add(new Occurrence(expense, terms, month, terms.dueDateIn(month)));
            }
        }
        return occurrences;
    }

    /** Os termos que valiam no mês: é o que faz um mês passado mostrar o valor da época. */
    static RecurringExpenseTerms termsIn(List<RecurringExpenseTerms> terms, YearMonth month) {
        return terms.stream().filter(candidate -> candidate.isValidIn(month)).findFirst().orElse(null);
    }

    static ExpenseStatus status(LocalDate dueDate, boolean paid, LocalDate today) {
        if (paid) {
            return ExpenseStatus.PAID;
        }
        return dueDate.isBefore(today) ? ExpenseStatus.OVERDUE : ExpenseStatus.PENDING;
    }
}
