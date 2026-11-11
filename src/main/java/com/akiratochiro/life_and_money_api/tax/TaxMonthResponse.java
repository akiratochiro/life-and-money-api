package com.akiratochiro.life_and_money_api.tax;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/** Os vencimentos de um mês (a "previsão do mês", no mês corrente), com os totais. */
public record TaxMonthResponse(
        @Schema(requiredMode = REQUIRED, example = "2026-11") YearMonth month,
        @Schema(requiredMode = REQUIRED, description = "Soma do previsto do que ainda não foi pago") BigDecimal expectedPending,
        @Schema(requiredMode = REQUIRED) BigDecimal paidTotal,
        @Schema(requiredMode = REQUIRED) List<TaxOccurrenceResponse> items,
        @Schema(requiredMode = REQUIRED) String notice
) {
}
