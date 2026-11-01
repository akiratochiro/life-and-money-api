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
 *
 * <p>{@code complete} é falso quando algum ano não pôde ser carregado: o calendário então só
 * conhece os fins de semana daquele ano.
 */
public final class HolidayCalendar {

    private final Set<LocalDate> holidays;
    private final boolean complete;

    public HolidayCalendar(Collection<Holiday> holidays, boolean complete) {
        this.holidays = holidays.stream().map(Holiday::date).collect(Collectors.toUnmodifiableSet());
        this.complete = complete;
    }

    /** Só fins de semana: o que vale quando os feriados não puderam ser carregados. */
    public static HolidayCalendar weekendsOnly() {
        return new HolidayCalendar(Set.of(), false);
    }

    public boolean isComplete() {
        return complete;
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
}
