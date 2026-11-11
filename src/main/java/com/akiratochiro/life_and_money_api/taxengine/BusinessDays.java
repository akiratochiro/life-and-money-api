package com.akiratochiro.life_and_money_api.taxengine;

import java.time.LocalDate;

/** O que o motor precisa saber do calendário. Em produção, o {@code HolidayCalendar}; nos testes, uma lista. */
@FunctionalInterface
public interface BusinessDays {
    boolean isBusinessDay(LocalDate date);
}
