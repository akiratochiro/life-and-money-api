package com.akiratochiro.life_and_money_api.holiday;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Dias úteis: nem sábado, nem domingo, nem feriado nacional. Quem monta o calendário garante que
 * os feriados cobrem os anos em que ele vai ser consultado, inclusive o ano seguinte, porque um
 * vencimento em 31/12 pode ter o prazo empurrado para janeiro.
 */
public final class HolidayCalendar {

    private final Set<LocalDate> holidays;

    public HolidayCalendar(Collection<Holiday> holidays) {
        this.holidays = holidays.stream().map(Holiday::date).collect(Collectors.toUnmodifiableSet());
    }

    public boolean isBusinessDay(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY && !holidays.contains(date);
    }

    /** O próprio dia, se for útil; senão, o próximo dia útil. */
    public LocalDate nextBusinessDayFrom(LocalDate date) {
        LocalDate candidate = date;
        while (!isBusinessDay(candidate)) {
            candidate = candidate.plusDays(1);
        }
        return candidate;
    }

    /** O próprio dia, se for útil; senão, o dia útil anterior (o DARF vence antes, não depois). */
    public LocalDate previousBusinessDayFrom(LocalDate date) {
        LocalDate candidate = date;
        while (!isBusinessDay(candidate)) {
            candidate = candidate.minusDays(1);
        }
        return candidate;
    }
}
