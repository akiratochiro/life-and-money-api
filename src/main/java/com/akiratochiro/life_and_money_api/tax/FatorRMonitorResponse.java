package com.akiratochiro.life_and_money_api.tax;

import com.akiratochiro.life_and_money_api.taxengine.FatorRProjection;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.YearMonth;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/**
 * O monitor do Fator R (decisão 30): o Fator R da próxima competência com o mês corrente projetado.
 * Fora do Simples com Fator R, só {@code applies=false} e o aviso.
 */
public record FatorRMonitorResponse(
        @Schema(requiredMode = REQUIRED, description = "Só no Simples com o Anexo pelo Fator R na próxima competência") boolean applies,
        @Schema(description = "A competência cujo Anexo está em jogo: a próxima", example = "2026-12") YearMonth competence,
        @Schema(description = "O mês projetado: o corrente", example = "2026-11") YearMonth projectedMonth,
        FatorRMonth projection,
        FatorRWindow window,
        @Schema(description = "Folha ÷ receita da janela") BigDecimal ratio,
        @Schema(description = "A meta da empresa, nunca abaixo do limite legal") BigDecimal target,
        @Schema(description = "O limite legal, que decide o Anexo") BigDecimal threshold,
        FatorRProjection.Status status,
        @Schema(description = "Ausente quando está na meta ou sem receita") FatorRSuggestion suggestion,
        @Schema(description = "Por que não há número (por exemplo, as regras de 2027)") String unavailable,
        @Schema(requiredMode = REQUIRED) String notice
) {
    public record FatorRMonth(
            @Schema(requiredMode = REQUIRED, description = "Notas ativas com competência no mês") BigDecimal invoices,
            @Schema(description = "O faturamento esperado que vale no mês, se houver") BigDecimal expectedRevenue,
            @Schema(requiredMode = REQUIRED, description = "O maior entre as notas e o faturamento esperado") BigDecimal revenue,
            @Schema(requiredMode = REQUIRED, description = "Pró-labores do mês: registrados, senão planejados") BigDecimal proLabore,
            @Schema(requiredMode = REQUIRED, description = "Despesas de folha do mês: pagas e fixas pendentes") BigDecimal otherPayroll
    ) {
    }

    public record FatorRWindow(
            @Schema(requiredMode = REQUIRED, example = "2025-12") YearMonth from,
            @Schema(requiredMode = REQUIRED, example = "2026-11") YearMonth to,
            @Schema(requiredMode = REQUIRED) BigDecimal revenue,
            @Schema(requiredMode = REQUIRED) BigDecimal payroll
    ) {
    }

    public record FatorRSuggestion(
            @Schema(requiredMode = REQUIRED, description = "Quanto falta na folha da janela para a meta") BigDecimal missingPayroll,
            @Schema(description = "O sócio \"sou eu\"") Long partnerId,
            @Schema(requiredMode = REQUIRED) BigDecimal currentProLabore,
            @Schema(requiredMode = REQUIRED) BigDecimal suggestedProLabore,
            @Schema(requiredMode = REQUIRED, description = "O pró-labore do mês já foi registrado: editar a retirada, não o planejado")
            boolean registered,
            @Schema(description = "Só abaixo do limite legal: DAS do V − DAS do III − INSS e IRRF a mais") BigDecimal savings,
            BigDecimal dasAnnexIII,
            BigDecimal dasAnnexV,
            BigDecimal extraProLaboreTaxes
    ) {
    }
}
