package com.akiratochiro.life_and_money_api.goal;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

public record SavingsGoalResponse(
        @Schema(requiredMode = REQUIRED) Long id,
        @Schema(requiredMode = REQUIRED) String name,
        @Schema(requiredMode = REQUIRED) BigDecimal targetAmount,
        @Schema(requiredMode = REQUIRED) LocalDate deadline,
        @Schema(requiredMode = REQUIRED, description = "Total já guardado em transações ligadas à meta") BigDecimal saved,
        @Schema(requiredMode = REQUIRED, description = "Quanto falta para a meta; zero quando já atingida") BigDecimal remaining,
        @Schema(requiredMode = REQUIRED, description = "Meses cheios entre o mês atual e o mês do prazo") long monthsLeft,
        @Schema(description = "Quanto guardar por mês para atingir a meta no prazo; ausente quando o prazo já passou")
        BigDecimal monthlyNeeded,
        @Schema(requiredMode = REQUIRED) GoalStatus status
) {
    public static SavingsGoalResponse from(SavingsGoal goal, GoalProgress progress) {
        return new SavingsGoalResponse(
                goal.getId(),
                goal.getName(),
                goal.getTargetAmount(),
                goal.getDeadline(),
                progress.saved(),
                progress.remaining(),
                progress.monthsLeft(),
                progress.monthlyNeeded(),
                progress.status()
        );
    }
}
