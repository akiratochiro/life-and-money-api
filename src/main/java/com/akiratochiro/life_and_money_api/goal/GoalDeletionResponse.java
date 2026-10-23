package com.akiratochiro.life_and_money_api.goal;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

public record GoalDeletionResponse(
        @Schema(requiredMode = REQUIRED) Long goalId,
        @Schema(requiredMode = REQUIRED, description = "Valor que estava guardado e permanece como poupança, sem meta")
        BigDecimal keptAsSavings
) {
}
