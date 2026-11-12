package com.akiratochiro.life_and_money_api.company;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.YearMonth;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

public record ExpectedRevenueResponse(
        @Schema(requiredMode = REQUIRED) Long companyId,
        @Schema(requiredMode = REQUIRED) BigDecimal amount,
        @Schema(requiredMode = REQUIRED, example = "2026-11") YearMonth validFrom,
        @Schema(description = "O último mês em que valeu; ausente no que vale hoje", example = "2026-12") YearMonth validTo
) {
    static ExpectedRevenueResponse from(ExpectedRevenue revenue) {
        return new ExpectedRevenueResponse(revenue.getCompanyId(), revenue.getAmount(), revenue.getValidFrom(), revenue.getValidTo());
    }
}
