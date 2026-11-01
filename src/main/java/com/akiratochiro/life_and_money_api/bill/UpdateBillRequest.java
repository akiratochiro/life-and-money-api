package com.akiratochiro.life_and_money_api.bill;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.YearMonth;

/**
 * A recorrência e o primeiro mês não mudam: são a identidade da conta. Para trocá-los, encerra-se
 * esta conta e cria-se outra.
 */
public record UpdateBillRequest(
        @NotBlank(message = "Informe o nome da conta.")
        @Size(max = 100, message = "O nome deve ter no máximo 100 caracteres.")
        String name,

        @NotNull(message = "Escolha uma categoria.")
        Long categoryId,

        @NotNull(message = "Informe o valor previsto.")
        @Positive(message = "O valor previsto deve ser maior que zero.")
        @Digits(integer = 17, fraction = 2, message = "O valor deve ter no máximo duas casas decimais.")
        BigDecimal amount,

        @NotNull(message = "Informe o dia de vencimento.")
        @Min(value = 1, message = "O dia de vencimento vai de 1 a 31.")
        @Max(value = 31, message = "O dia de vencimento vai de 1 a 31.")
        Integer dueDay,

        @Schema(example = "2027-10", description = "Último mês; ausente para não ter fim")
        YearMonth endMonth
) {
}
