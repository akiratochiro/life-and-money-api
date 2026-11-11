package com.akiratochiro.life_and_money_api.companyexpense;

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

public record RecurringExpenseRequest(
        @NotBlank(message = "Informe o nome da despesa.")
        @Size(max = 100, message = "O nome deve ter no máximo 100 caracteres.")
        String name,

        @NotNull(message = "Informe o valor previsto.")
        @Positive(message = "O valor precisa ser maior que zero.")
        @Digits(integer = 17, fraction = 2, message = "Valor inválido.")
        BigDecimal amount,

        @NotNull(message = "Escolha a categoria.")
        Long categoryId,

        @NotNull(message = "Escolha o dia do vencimento.")
        @Min(value = 1, message = "O dia vai de 1 a 31.") @Max(value = 31, message = "O dia vai de 1 a 31.")
        Integer dueDay,

        @Schema(description = "Primeiro mês; só na criação", example = "2026-11")
        YearMonth startMonth,

        @Schema(description = "Último mês, opcional", example = "2027-10")
        YearMonth endMonth
) {
}
