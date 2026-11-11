package com.akiratochiro.life_and_money_api.companyexpense;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Pagar um mês: sem valor, vale o previsto; sem data, o vencimento (ou hoje, se ele ainda não chegou). */
public record PayRecurringExpenseRequest(
        @Schema(description = "O valor pago; sem ele, o previsto")
        @Positive(message = "O valor precisa ser maior que zero.")
        @Digits(integer = 17, fraction = 2, message = "Valor inválido.")
        BigDecimal amount,

        @Schema(description = "O dia do pagamento; não pode estar no futuro")
        LocalDate date
) {
}
