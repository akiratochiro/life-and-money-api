package com.akiratochiro.life_and_money_api.goal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SavingsGoalRequest(
        @NotBlank(message = "Informe o nome da meta.")
        @Size(max = 100, message = "O nome deve ter no máximo 100 caracteres.")
        String name,

        @NotNull(message = "Informe o valor da meta.")
        @Positive(message = "O valor da meta deve ser maior que zero.")
        @Digits(integer = 17, fraction = 2, message = "O valor deve ter no máximo duas casas decimais.")
        BigDecimal targetAmount,

        @NotNull(message = "Informe o prazo da meta.")
        @Schema(description = "Precisa ser um mês posterior ao mês atual")
        LocalDate deadline
) {
}
