package com.akiratochiro.life_and_money_api.tax;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PayTaxRequest(
        @Schema(description = "O valor pago; ausente nas declarações, que só são marcadas como entregues")
        @Positive(message = "O valor precisa ser maior que zero.")
        @Digits(integer = 17, fraction = 2, message = "Valor inválido.")
        BigDecimal amount,
        @Schema(description = "O dia do pagamento (ou da entrega); não pode estar no futuro")
        @NotNull(message = "Informe a data.")
        LocalDate date
) {
}
