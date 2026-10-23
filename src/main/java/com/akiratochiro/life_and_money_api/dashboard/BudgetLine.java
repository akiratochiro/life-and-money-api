package com.akiratochiro.life_and_money_api.dashboard;

import com.akiratochiro.life_and_money_api.budget.BudgetMode;
import com.akiratochiro.life_and_money_api.category.CategoryType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

public record BudgetLine(
        @Schema(requiredMode = REQUIRED) Long categoryId,
        @Schema(requiredMode = REQUIRED) String categoryName,
        @Schema(requiredMode = REQUIRED) CategoryType categoryType,
        @Schema(requiredMode = REQUIRED) BudgetMode mode,
        @Schema(requiredMode = REQUIRED, description = "Valor planejado como foi definido: reais ou percentual")
        BigDecimal limitValue,
        @Schema(requiredMode = REQUIRED, description = "Limite já convertido em reais para o mês consultado")
        BigDecimal effectiveLimit,
        @Schema(requiredMode = REQUIRED) BigDecimal spent,
        @Schema(requiredMode = REQUIRED, description = "Limite menos o gasto; negativo quando o limite foi ultrapassado")
        BigDecimal remaining,
        @Schema(description = "Ausente quando o limite do mês é zero, caso em que não há percentual a calcular")
        BigDecimal usedPercentage,
        @Schema(requiredMode = REQUIRED) BudgetStatus status
) {
}
