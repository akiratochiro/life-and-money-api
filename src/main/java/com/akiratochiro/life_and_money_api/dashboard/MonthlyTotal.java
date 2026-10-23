package com.akiratochiro.life_and_money_api.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.YearMonth;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

public record MonthlyTotal(
        @Schema(requiredMode = REQUIRED) YearMonth month,
        @Schema(requiredMode = REQUIRED) BigDecimal income,
        @Schema(requiredMode = REQUIRED) BigDecimal expense,
        @Schema(requiredMode = REQUIRED) BigDecimal saving,
        @Schema(requiredMode = REQUIRED, description = "Falso apenas para o mês corrente, que ainda pode mudar")
        boolean closed
) {
}
