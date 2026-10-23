package com.akiratochiro.life_and_money_api.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

public record ExpensesByCategoryResponse(
        @Schema(requiredMode = REQUIRED) YearMonth month,
        @Schema(requiredMode = REQUIRED, description = "Total de despesas do mês") BigDecimal total,
        @Schema(requiredMode = REQUIRED, description = "Categorias de despesa com movimento no mês, da maior para a menor")
        List<CategoryExpense> categories
) {
}
