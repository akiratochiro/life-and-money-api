package com.akiratochiro.life_and_money_api.companyoverview;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.YearMonth;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/** Os cards do mês da visão geral. Resultado = receita − despesas − impostos; retirada fica fora. */
public record CompanyOverviewResponse(
        @Schema(requiredMode = REQUIRED, example = "2026-11") YearMonth month,
        @Schema(requiredMode = REQUIRED, description = "O mês ainda não acabou: os valores são previsão") boolean open,
        @Schema(requiredMode = REQUIRED, description = "Notas ativas com competência no mês") BigDecimal revenue,
        @Schema(requiredMode = REQUIRED) Expenses expenses,
        @Schema(requiredMode = REQUIRED) Taxes taxes,
        @Schema(requiredMode = REQUIRED) BigDecimal result,
        @Schema(requiredMode = REQUIRED) Withdrawals withdrawals,
        @Schema(requiredMode = REQUIRED) String notice
) {
    public record Expenses(
            @Schema(requiredMode = REQUIRED) BigDecimal paid,
            @Schema(requiredMode = REQUIRED, description = "Previsto das fixas ainda não pagas") BigDecimal pending,
            @Schema(requiredMode = REQUIRED, description = "Sem a categoria Impostos, que entra pelo card de impostos") BigDecimal total
    ) {
    }

    public record Taxes(
            @Schema(requiredMode = REQUIRED, description = "Os já pagos, pelo valor pago") BigDecimal paid,
            @Schema(requiredMode = REQUIRED, description = "Os ainda não pagos, pelo previsto") BigDecimal expected,
            @Schema(requiredMode = REQUIRED) BigDecimal total,
            @Schema(requiredMode = REQUIRED, description = "Impostos da competência sem valor previsto, contados como zero") int withoutExpected
    ) {
    }

    public record Withdrawals(
            @Schema(requiredMode = REQUIRED, description = "Bruto dos pagos no mês, pela data do pagamento") BigDecimal total,
            @Schema(requiredMode = REQUIRED) int count
    ) {
    }
}
