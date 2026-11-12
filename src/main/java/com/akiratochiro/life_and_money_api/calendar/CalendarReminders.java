package com.akiratochiro.life_and_money_api.calendar;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/** O que pede atenção: os atrasados e o que vence de hoje até daqui a 7 dias, sem os pagos. */
public record CalendarReminders(
        @Schema(requiredMode = REQUIRED) List<CalendarEvent> overdue,
        @Schema(requiredMode = REQUIRED) List<CalendarEvent> nextDays,
        @Schema(requiredMode = REQUIRED) int count
) {
}
