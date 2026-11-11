package com.akiratochiro.life_and_money_api.withdrawal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

public record WithdrawalRequest(
        @NotNull(message = "Escolha o sócio.") Long partnerId,
        @NotNull(message = "Escolha o tipo de retirada.") WithdrawalKind kind,
        @Schema(description = "O mês da folha do pró-labore", example = "2026-11")
        @NotNull(message = "Informe o mês da folha.") YearMonth competenceMonth,
        @NotNull(message = "Informe a data do pagamento.") LocalDate paymentDate,
        @NotNull(message = "Informe o valor bruto.")
        @Positive(message = "O valor precisa ser maior que zero.")
        @Digits(integer = 17, fraction = 2, message = "Valor inválido.")
        BigDecimal grossAmount,
        @Schema(description = "Só nos meses que o app não calcula (antes de 2026): o INSS retido, como no holerite")
        @PositiveOrZero(message = "O valor não pode ser negativo.") @Digits(integer = 17, fraction = 2, message = "Valor inválido.")
        BigDecimal inssAmount,
        @Schema(description = "Só nos meses que o app não calcula: o IRRF retido, como no holerite")
        @PositiveOrZero(message = "O valor não pode ser negativo.") @Digits(integer = 17, fraction = 2, message = "Valor inválido.")
        BigDecimal irrfAmount
) {
}
