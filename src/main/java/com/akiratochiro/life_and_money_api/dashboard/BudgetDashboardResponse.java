package com.akiratochiro.life_and_money_api.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

public record BudgetDashboardResponse(
        @Schema(requiredMode = REQUIRED) YearMonth month,
        @Schema(requiredMode = REQUIRED, description = "Falso apenas para o mês corrente, que ainda pode mudar")
        boolean closed,
        @Schema(requiredMode = REQUIRED, description = "Receita realizada no mês, base dos orçamentos em percentual")
        BigDecimal income,
        @Schema(requiredMode = REQUIRED) List<BudgetLine> items
) {
}
