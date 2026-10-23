package com.akiratochiro.life_and_money_api.budget;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record BudgetRequest(
        @NotNull(message = "Escolha entre valor fixo e percentual da receita.")
        BudgetMode mode,

        @NotNull(message = "Informe o valor planejado.")
        @Positive(message = "O valor planejado deve ser maior que zero.")
        @Digits(integer = 17, fraction = 2, message = "O valor deve ter no máximo duas casas decimais.")
        @Schema(description = "Valor em reais quando mode é AMOUNT, percentual da receita do mês quando é PERCENTAGE")
        BigDecimal limitValue
) {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    /**
     * Validação que depende de dois campos, por isso fica num método e não numa anotação de campo.
     * O {@code @JsonIgnore} existe só para que este método não apareça como um campo do corpo
     * da requisição no OpenAPI: a validação continua rodando, porque o Bean Validation não
     * consulta as anotações do Jackson.
     */
    @JsonIgnore
    @AssertTrue(message = "O percentual não pode passar de 100.")
    public boolean isPercentageWithinLimit() {
        if (mode != BudgetMode.PERCENTAGE || limitValue == null) {
            return true;
        }
        return limitValue.compareTo(ONE_HUNDRED) <= 0;
    }
}
