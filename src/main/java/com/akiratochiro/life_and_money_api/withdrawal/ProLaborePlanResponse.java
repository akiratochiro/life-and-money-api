package com.akiratochiro.life_and_money_api.withdrawal;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.YearMonth;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

public record ProLaborePlanResponse(
        @Schema(requiredMode = REQUIRED) Long partnerId,
        @Schema(requiredMode = REQUIRED) BigDecimal grossAmount,
        @Schema(requiredMode = REQUIRED, example = "2026-11") YearMonth validFrom
) {
    static ProLaborePlanResponse from(ProLaborePlan plan) {
        return new ProLaborePlanResponse(plan.getPartnerId(), plan.getGrossAmount(), plan.getValidFrom());
    }
}
