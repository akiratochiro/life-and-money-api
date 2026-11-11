package com.akiratochiro.life_and_money_api.companyexpense;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Uma despesa avulsa, já paga. No pagamento de uma fixa, só o valor e a data mudam. */
public record OneOffExpenseRequest(
        @NotBlank(message = "Informe o nome da despesa.")
        @Size(max = 100, message = "O nome deve ter no máximo 100 caracteres.")
        String name,

        @NotNull(message = "Informe o valor.")
        @Positive(message = "O valor precisa ser maior que zero.")
        @Digits(integer = 17, fraction = 2, message = "Valor inválido.")
        BigDecimal amount,

        @NotNull(message = "Escolha a categoria.")
        Long categoryId,

        @Schema(description = "O dia em que foi paga; não pode estar no futuro")
        @NotNull(message = "Informe a data.")
        LocalDate date
) {
}
