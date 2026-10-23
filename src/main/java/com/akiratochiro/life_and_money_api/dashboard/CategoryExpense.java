package com.akiratochiro.life_and_money_api.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

public record CategoryExpense(
        @Schema(requiredMode = REQUIRED) Long categoryId,
        @Schema(requiredMode = REQUIRED) String name,
        @Schema(requiredMode = REQUIRED) BigDecimal total,
        @Schema(requiredMode = REQUIRED, description = "Participação da categoria no total do mês, em percentual")
        BigDecimal percentage
) {
}
