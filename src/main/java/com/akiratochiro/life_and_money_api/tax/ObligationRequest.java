package com.akiratochiro.life_and_money_api.tax;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.YearMonth;

/** Uma obrigação personalizada: um imposto do Lucro Presumido, uma taxa municipal anual. */
public record ObligationRequest(
        @NotBlank(message = "Informe o nome da obrigação.") @Size(max = 100, message = "O nome deve ter no máximo 100 caracteres.")
        String name,
        @NotNull(message = "Escolha a recorrência.") Recurrence recurrence,
        @NotNull(message = "Escolha o dia do vencimento.")
        @Min(value = 1, message = "O dia vai de 1 a 31.") @Max(value = 31, message = "O dia vai de 1 a 31.") Integer dueDay,
        @Schema(description = "Só na anual: o mês do vencimento (1 a 12)")
        @Min(value = 1, message = "O mês vai de 1 a 12.") @Max(value = 12, message = "O mês vai de 1 a 12.") Integer dueMonth,
        @NotNull(message = "Escolha o que fazer em dia sem expediente bancário.") DueRule dueRule,
        @Schema(description = "Valor fixo, se houver; sem ele, o valor é informado no pagamento")
        @Positive(message = "O valor precisa ser maior que zero.") @Digits(integer = 17, fraction = 2, message = "Valor inválido.")
        BigDecimal fixedAmount,
        @NotNull(message = "Informe o primeiro mês.") YearMonth startMonth,
        YearMonth endMonth
) {
}
