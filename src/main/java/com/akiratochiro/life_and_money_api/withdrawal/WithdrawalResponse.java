package com.akiratochiro.life_and_money_api.withdrawal;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

public record WithdrawalResponse(
        @Schema(requiredMode = REQUIRED) Long id,
        @Schema(requiredMode = REQUIRED) Long partnerId,
        @Schema(requiredMode = REQUIRED) WithdrawalKind kind,
        @Schema(example = "2026-11") YearMonth competenceMonth,
        @Schema(requiredMode = REQUIRED) LocalDate paymentDate,
        @Schema(requiredMode = REQUIRED) BigDecimal grossAmount,
        @Schema(requiredMode = REQUIRED) BigDecimal inssAmount,
        @Schema(requiredMode = REQUIRED) BigDecimal irrfAmount,
        @Schema(requiredMode = REQUIRED) BigDecimal netAmount,
        @Schema(requiredMode = REQUIRED, description = "Falso quando o INSS e o IRRF foram informados (mês que o app não calcula)")
        boolean calculated,
        @Schema(requiredMode = REQUIRED) List<String> warnings
) {
}
