package com.akiratochiro.life_and_money_api.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

public record Averages(
        @Schema(requiredMode = REQUIRED) BigDecimal income,
        @Schema(requiredMode = REQUIRED) BigDecimal expense,
        @Schema(requiredMode = REQUIRED) BigDecimal saving,
        @Schema(requiredMode = REQUIRED, description = "Quantos meses fechados entraram na média") int basedOnMonths
) {
}
