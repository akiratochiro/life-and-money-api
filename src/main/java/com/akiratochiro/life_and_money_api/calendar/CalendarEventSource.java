package com.akiratochiro.life_and_money_api.calendar;

import java.time.LocalDate;
import java.util.List;

/**
 * Uma feature que tem vencimentos (decisão 31). O calendário define a interface e as features a
 * implementam, como {@code transaction} e {@code goal} (decisão 3): o {@code calendar} nunca importa
 * quem o alimenta, e uma feature nova entra só implementando isto.
 */
public interface CalendarEventSource {

    CalendarEvent.Scope scope();

    /**
     * Os eventos do usuário com vencimento entre {@code from} e {@code to}, inclusive. Com
     * {@code companyId}, só os dessa empresa; a fonte PJ confere que ela é do usuário (404 se não for).
     * As fontes PF nunca recebem {@code companyId}.
     */
    List<CalendarEvent> eventsBetween(Long userId, LocalDate from, LocalDate to, Long companyId);

    /** Desde quando um atrasado ainda vira lembrete: cada fonte sabe quanto custa olhar para trás. */
    LocalDate overdueSince(LocalDate today);
}
