package com.akiratochiro.life_and_money_api.transaction;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TransactionRequest(
        @NotNull(message = "Escolha uma categoria.")
        Long categoryId,

        @Schema(description = "Meta de poupança a creditar; apenas para categorias do tipo SAVING")
        Long goalId,

        @NotNull(message = "Informe o valor.")
        @Positive(message = "O valor deve ser maior que zero.")
        @Digits(integer = 17, fraction = 2, message = "O valor deve ter no máximo duas casas decimais.")
        BigDecimal amount,

        @NotNull(message = "Informe a data.")
        LocalDate transactionDate,

        @Size(max = 255, message = "A descrição deve ter no máximo 255 caracteres.")
        String description
) {
}
