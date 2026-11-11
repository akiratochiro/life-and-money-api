package com.akiratochiro.life_and_money_api.companyoverview;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/** Um vencimento ainda não pago: imposto (ou declaração) ou despesa fixa. */
public record UpcomingItem(
        @Schema(requiredMode = REQUIRED) Source source,
        @Schema(requiredMode = REQUIRED) String name,
        @Schema(requiredMode = REQUIRED) LocalDate dueDate,
        @Schema(description = "O previsto; ausente quando não há (declaração, personalizada sem valor fixo)") BigDecimal amount,
        @Schema(requiredMode = REQUIRED) Status status,
        @Schema(requiredMode = REQUIRED, description = "O mês do vencimento, para abrir a aba nele") YearMonth month
) {
    public enum Source { TAX, EXPENSE }

    public enum Status { PENDING, OVERDUE }
}
