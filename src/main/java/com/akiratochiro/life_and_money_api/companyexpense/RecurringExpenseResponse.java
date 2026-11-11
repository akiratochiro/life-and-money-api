package com.akiratochiro.life_and_money_api.companyexpense;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/** Uma despesa fixa com os termos atuais (e, no export, o histórico). */
public record RecurringExpenseResponse(
        @Schema(requiredMode = REQUIRED) Long id,
        @Schema(requiredMode = REQUIRED, example = "2026-11") YearMonth startMonth,
        @Schema(example = "2027-10") YearMonth endMonth,
        @Schema(requiredMode = REQUIRED) String name,
        @Schema(requiredMode = REQUIRED) BigDecimal amount,
        @Schema(requiredMode = REQUIRED) Long categoryId,
        @Schema(requiredMode = REQUIRED) int dueDay,
        @Schema(description = "Todo o histórico de termos; só no export") List<Terms> history
) {
    public record Terms(
            @Schema(requiredMode = REQUIRED) String name,
            @Schema(requiredMode = REQUIRED) BigDecimal amount,
            @Schema(requiredMode = REQUIRED) Long categoryId,
            @Schema(requiredMode = REQUIRED) int dueDay,
            @Schema(requiredMode = REQUIRED) YearMonth validFrom,
            YearMonth validTo
    ) {
        static Terms from(RecurringExpenseTerms terms) {
            return new Terms(terms.getName(), terms.getAmount(), terms.getCategoryId(), terms.getDueDay(),
                    terms.getValidFrom(), terms.getValidTo());
        }
    }

    static RecurringExpenseResponse from(RecurringExpense expense, RecurringExpenseTerms current, List<RecurringExpenseTerms> history) {
        return new RecurringExpenseResponse(expense.getId(), expense.getStartMonth(), expense.getEndMonth(),
                current.getName(), current.getAmount(), current.getCategoryId(), current.getDueDay(),
                history == null ? null : history.stream().map(Terms::from).toList());
    }
}
