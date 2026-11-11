package com.akiratochiro.life_and_money_api.tax;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.YearMonth;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

public record ObligationResponse(
        @Schema(requiredMode = REQUIRED) Long id,
        @Schema(requiredMode = REQUIRED) ObligationKind kind,
        @Schema(requiredMode = REQUIRED) String name,
        @Schema(requiredMode = REQUIRED) Recurrence recurrence,
        @Schema(requiredMode = REQUIRED) int dueDay,
        Integer dueMonth,
        @Schema(requiredMode = REQUIRED) DueRule dueRule,
        BigDecimal fixedAmount,
        @Schema(requiredMode = REQUIRED, description = "Do sistema: não pode ser excluída, só desativada") boolean system,
        @Schema(requiredMode = REQUIRED) boolean active,
        YearMonth startMonth,
        YearMonth endMonth
) {
    static ObligationResponse from(TaxObligation o) {
        return new ObligationResponse(o.getId(), o.getKind(), o.getName(), o.getRecurrence(), o.getDueDay(), o.getDueMonth(),
                o.getDueRule(), o.getFixedAmount(), o.isSystem(), o.isActive(), o.getStartMonth(), o.getEndMonth());
    }
}
