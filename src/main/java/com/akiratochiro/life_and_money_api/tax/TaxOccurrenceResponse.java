package com.akiratochiro.life_and_money_api.tax;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/** Um vencimento: obrigação + competência, com o previsto, a memória de cálculo e o pagamento. */
public record TaxOccurrenceResponse(
        @Schema(requiredMode = REQUIRED) Long obligationId,
        @Schema(requiredMode = REQUIRED) ObligationKind kind,
        @Schema(requiredMode = REQUIRED) String name,
        @Schema(requiredMode = REQUIRED, description = "O mês de apuração; nas anuais, janeiro do ano-calendário", example = "2026-10")
        YearMonth competence,
        @Schema(requiredMode = REQUIRED) LocalDate dueDate,
        @Schema(requiredMode = REQUIRED) Status status,
        @Schema(description = "Ausente quando o app não calcula (veja note)") BigDecimal expectedAmount,
        @Schema(description = "Por que não há previsto, ou um aviso sobre ele") String note,
        @Schema(description = "De onde veio o previsto") TaxCalculation calculation,
        @Schema(description = "Presente quando pago (ou entregue, nas declarações)") Payment payment,
        @Schema(requiredMode = REQUIRED, description = "Declaração anual: marca-se como entregue, sem valor") boolean declaration
) {
    public enum Status { PAID, PENDING, OVERDUE }

    public record Payment(
            @Schema(description = "O valor pago, da despesa de impostos") BigDecimal paidAmount,
            @Schema(description = "O dia do pagamento, da despesa de impostos") LocalDate paidDate,
            @Schema(description = "O dia da entrega, nas declarações") LocalDate deliveredOn,
            @Schema(description = "O previsto no momento do pagamento") BigDecimal expectedAtPayment,
            @Schema(description = "Pago − previsto, quando os dois existem") BigDecimal difference,
            @Schema(description = "A despesa de impostos criada com o pagamento") Long expenseId
    ) {
    }
}
