package com.akiratochiro.life_and_money_api.companyexpense;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/** O mês inteiro numa lista: avulsas, fixas pagas e fixas pendentes, com os totais. */
public record ExpenseMonthResponse(
        @Schema(requiredMode = REQUIRED, example = "2026-11") YearMonth month,
        @Schema(requiredMode = REQUIRED) BigDecimal paidTotal,
        @Schema(requiredMode = REQUIRED, description = "Previsto das fixas ainda não pagas") BigDecimal pendingTotal,
        @Schema(requiredMode = REQUIRED) List<Item> items
) {
    public enum Kind { ONE_OFF, RECURRING }

    public record Item(
            @Schema(requiredMode = REQUIRED) Kind kind,
            @Schema(description = "A despesa paga; ausente numa fixa pendente") Long expenseId,
            @Schema(description = "A fixa; ausente numa avulsa") Long recurringExpenseId,
            @Schema(requiredMode = REQUIRED) String name,
            @Schema(requiredMode = REQUIRED) Long categoryId,
            @Schema(requiredMode = REQUIRED, description = "O valor pago, ou o previsto se ainda não foi pago") BigDecimal amount,
            @Schema(description = "O previsto da fixa no mês") BigDecimal expectedAmount,
            @Schema(requiredMode = REQUIRED, description = "A data do pagamento, ou o vencimento se ainda não foi pago")
            LocalDate date,
            @Schema(description = "O vencimento da fixa no mês") LocalDate dueDate,
            @Schema(requiredMode = REQUIRED) ExpenseStatus status,
            @Schema(requiredMode = REQUIRED) boolean hasReceipt
    ) {
    }
}
