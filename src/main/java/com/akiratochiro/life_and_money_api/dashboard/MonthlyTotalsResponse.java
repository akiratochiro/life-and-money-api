package com.akiratochiro.life_and_money_api.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

public record MonthlyTotalsResponse(
        @Schema(requiredMode = REQUIRED, description = "Um item por mês do intervalo pedido, do mais antigo para o mais recente")
        List<MonthlyTotal> months,
        @Schema(description = "Ausente enquanto o usuário não tem nenhum mês fechado desde o cadastro")
        Averages averages
) {
}
