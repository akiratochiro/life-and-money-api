package com.akiratochiro.life_and_money_api.bill;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/** Uma conta no export, com todo o histórico de termos. */
public record BillExport(
        @Schema(requiredMode = REQUIRED) Long id,
        @Schema(requiredMode = REQUIRED) BillRecurrence recurrence,
        @Schema(requiredMode = REQUIRED, example = "2026-11") YearMonth startMonth,
        @Schema(example = "2027-10") YearMonth endMonth,
        @Schema(requiredMode = REQUIRED) List<Terms> terms
) {

    public record Terms(
            @Schema(requiredMode = REQUIRED) String name,
            @Schema(requiredMode = REQUIRED) Long categoryId,
            @Schema(requiredMode = REQUIRED) BigDecimal amount,
            @Schema(requiredMode = REQUIRED) int dueDay,
            @Schema(requiredMode = REQUIRED, example = "2026-11") YearMonth validFrom,
            @Schema(example = "2027-10") YearMonth validTo
    ) {
        static Terms from(BillTerms terms) {
            return new Terms(terms.getName(), terms.getCategoryId(), terms.getAmount(),
                    terms.getDueDay(), terms.getValidFrom(), terms.getValidTo());
        }
    }
}
