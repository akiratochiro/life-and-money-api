package com.akiratochiro.life_and_money_api.invoice;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

public record RevenueResponse(
        @Schema(requiredMode = REQUIRED) RevenueGranularity granularity,
        @Schema(requiredMode = REQUIRED, description = "Início do intervalo, alargado até a segunda (semana) ou o dia 1 (mês)")
        LocalDate from,
        @Schema(requiredMode = REQUIRED, description = "Fim do intervalo, alargado até o domingo (semana) ou o último dia (mês)")
        LocalDate to,
        @Schema(requiredMode = REQUIRED, description = "Serviço menos desconto incondicional das notas ativas")
        BigDecimal total,
        @Schema(requiredMode = REQUIRED) List<Point> points
) {
    public record Point(
            @Schema(requiredMode = REQUIRED) LocalDate start,
            @Schema(requiredMode = REQUIRED) LocalDate end,
            @Schema(requiredMode = REQUIRED) BigDecimal amount,
            @Schema(requiredMode = REQUIRED) int invoices
    ) {
    }
}
