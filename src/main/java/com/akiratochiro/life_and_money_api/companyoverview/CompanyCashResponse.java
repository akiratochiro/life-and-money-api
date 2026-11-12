package com.akiratochiro.life_and_money_api.companyoverview;

import com.akiratochiro.life_and_money_api.tax.ObligationKind;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/**
 * O caixa da empresa hoje (decisão 29): o saldo da conta, o que dele já é do governo e o que sobra.
 * Não depende do mês escolhido na visão geral.
 */
public record CompanyCashResponse(
        @Schema(requiredMode = REQUIRED, description = "O dia do cálculo: hoje") LocalDate asOf,
        @Schema(requiredMode = REQUIRED) BigDecimal openingBalance,
        @Schema(requiredMode = REQUIRED, description = "O saldo inicial vale do começo deste dia") LocalDate openingBalanceDate,
        @Schema(requiredMode = REQUIRED) Balance balance,
        @Schema(requiredMode = REQUIRED) Provision provision,
        @Schema(requiredMode = REQUIRED, description = "Saldo − provisão; pode ser negativo") BigDecimal available,
        @Schema(description = "Ausente quando a empresa não tem DAS (Lucro Presumido ou Real)") GovernmentShare governmentShare,
        @Schema(requiredMode = REQUIRED) String notice
) {
    public record Balance(
            @Schema(requiredMode = REQUIRED) BigDecimal total,
            @Schema(requiredMode = REQUIRED, description = "Líquido das notas ativas emitidas desde o saldo inicial") BigDecimal revenue,
            @Schema(requiredMode = REQUIRED, description = "Despesas pagas desde o saldo inicial, com os impostos pagos") BigDecimal expenses,
            @Schema(requiredMode = REQUIRED, description = "Líquido das retiradas pagas desde o saldo inicial") BigDecimal withdrawals
    ) {
    }

    public record Provision(
            @Schema(requiredMode = REQUIRED) BigDecimal total,
            @Schema(requiredMode = REQUIRED, description = "Impostos da competência atual, ainda não pagos") BigDecimal currentMonth,
            @Schema(requiredMode = REQUIRED, description = "Impostos de competências passadas, ainda no prazo") BigDecimal dueSoon,
            @Schema(requiredMode = REQUIRED, description = "Impostos vencidos e não pagos (até 3 meses de vencimento)") BigDecimal overdue,
            @Schema(requiredMode = REQUIRED, description = "Impostos sem valor previsto, que ficaram fora") int withoutExpected
    ) {
    }

    public record GovernmentShare(
            @Schema(requiredMode = REQUIRED, example = "2026-11") YearMonth month,
            @Schema(requiredMode = REQUIRED, description = "Notas ativas com competência no mês") BigDecimal revenue,
            @Schema(requiredMode = REQUIRED, description = "O DAS dessa receita: pago, ou o previsto") BigDecimal amount,
            @Schema(description = "amount ÷ revenue; ausente no MEI (valor fixo) ou sem receita") BigDecimal effectiveRate,
            @Schema(requiredMode = REQUIRED) ObligationKind kind
    ) {
    }
}
