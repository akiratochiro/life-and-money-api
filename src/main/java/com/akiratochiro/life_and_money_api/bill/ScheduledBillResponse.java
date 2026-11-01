package com.akiratochiro.life_and_money_api.bill;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.YearMonth;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/** A conta com os termos em vigor hoje (ou os mais recentes). */
public record ScheduledBillResponse(
        @Schema(requiredMode = REQUIRED) Long id,
        @Schema(requiredMode = REQUIRED) String name,
        @Schema(requiredMode = REQUIRED) Long categoryId,
        @Schema(requiredMode = REQUIRED) BigDecimal amount,
        @Schema(requiredMode = REQUIRED) int dueDay,
        @Schema(requiredMode = REQUIRED) BillRecurrence recurrence,
        @Schema(requiredMode = REQUIRED, example = "2026-11") YearMonth startMonth,
        @Schema(example = "2027-10", description = "Ausente enquanto a conta não tem fim")
        YearMonth endMonth
) {

    static ScheduledBillResponse from(ScheduledBill bill, BillTerms terms) {
        return new ScheduledBillResponse(
                bill.getId(),
                terms.getName(),
                terms.getCategoryId(),
                terms.getAmount(),
                terms.getDueDay(),
                bill.getRecurrence(),
                bill.getStartMonth(),
                bill.getEndMonth());
    }
}
