package com.akiratochiro.life_and_money_api.bill;

import com.akiratochiro.life_and_money_api.category.CategoryType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/** Uma ocorrência calculada: a conta num mês, com os termos que valiam naquele mês. */
public record BillOccurrenceResponse(
        @Schema(requiredMode = REQUIRED) Long billId,
        @Schema(requiredMode = REQUIRED, example = "2026-11", description = "Identifica a ocorrência junto com billId")
        YearMonth month,
        @Schema(requiredMode = REQUIRED) LocalDate dueDate,
        @Schema(requiredMode = REQUIRED) String name,
        @Schema(requiredMode = REQUIRED) Long categoryId,
        @Schema(requiredMode = REQUIRED, description = "EXPENSE é a pagar; INCOME é a receber")
        CategoryType type,
        @Schema(requiredMode = REQUIRED) BillRecurrence recurrence,
        @Schema(requiredMode = REQUIRED) BigDecimal expectedAmount,
        @Schema(requiredMode = REQUIRED) BillStatus status
) {

    static BillOccurrenceResponse of(BillCalendar.Occurrence occurrence, CategoryType type, LocalDate today) {
        return new BillOccurrenceResponse(
                occurrence.bill().getId(),
                occurrence.month(),
                occurrence.dueDate(),
                occurrence.terms().getName(),
                occurrence.terms().getCategoryId(),
                type,
                occurrence.bill().getRecurrence(),
                occurrence.terms().getAmount(),
                BillStatus.of(false, occurrence.dueDate(), today));
    }
}
