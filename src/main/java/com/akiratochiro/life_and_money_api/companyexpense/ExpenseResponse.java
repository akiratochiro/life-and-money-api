package com.akiratochiro.life_and_money_api.companyexpense;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

public record ExpenseResponse(
        @Schema(requiredMode = REQUIRED) Long id,
        @Schema(requiredMode = REQUIRED) String name,
        @Schema(requiredMode = REQUIRED) BigDecimal amount,
        @Schema(requiredMode = REQUIRED) Long categoryId,
        @Schema(requiredMode = REQUIRED) LocalDate date,
        @Schema(requiredMode = REQUIRED) boolean hasReceipt,
        @Schema(description = "Presente quando é o pagamento de uma despesa fixa") Long recurringExpenseId,
        @Schema(example = "2026-10") YearMonth occurrenceMonth
) {
    public static ExpenseResponse from(CompanyExpense expense) {
        return new ExpenseResponse(expense.getId(), expense.getName(), expense.getAmount(), expense.getCategoryId(),
                expense.getExpenseDate(), expense.getAttachmentId() != null, expense.getRecurringExpenseId(),
                expense.getOccurrenceMonth());
    }
}
