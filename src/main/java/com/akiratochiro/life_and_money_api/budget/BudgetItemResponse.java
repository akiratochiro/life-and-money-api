package com.akiratochiro.life_and_money_api.budget;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.YearMonth;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

public record BudgetItemResponse(
        @Schema(requiredMode = REQUIRED) Long id,
        @Schema(requiredMode = REQUIRED) Long categoryId,
        @Schema(requiredMode = REQUIRED) BudgetMode mode,
        @Schema(requiredMode = REQUIRED, description = "Valor em reais (AMOUNT) ou percentual da receita do mês (PERCENTAGE)")
        BigDecimal limitValue,
        @Schema(requiredMode = REQUIRED, description = "Primeiro mês de vigência") YearMonth validFrom,
        @Schema(description = "Último mês de vigência; ausente enquanto o item está em vigor") YearMonth validTo,
        @Schema(requiredMode = REQUIRED) boolean active
) {

    public static BudgetItemResponse from(BudgetItem budgetItem) {
        return new BudgetItemResponse(
                budgetItem.getId(),
                budgetItem.getCategoryId(),
                budgetItem.getMode(),
                budgetItem.getLimitValue(),
                budgetItem.getValidFrom(),
                budgetItem.getValidTo(),
                budgetItem.isActive()
        );
    }
}
