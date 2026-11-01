package com.akiratochiro.life_and_money_api;

import com.akiratochiro.life_and_money_api.holiday.Holiday;
import com.akiratochiro.life_and_money_api.holiday.HolidaySource;
import com.akiratochiro.life_and_money_api.holiday.HolidaySourceException;

import java.time.LocalDate;
import java.time.MonthDay;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Feriados dos testes de integração, sem rede: as datas fixas de qualquer ano e, para 2026 e
 * 2027, também as móveis, iguais às que a BrasilAPI devolve. Um teste pode simular a BrasilAPI
 * fora do ar com {@link #failing(boolean)}.
 */
public class FixedHolidaySource implements HolidaySource {

    private static final Map<MonthDay, String> FIXED = Map.of(
            MonthDay.of(1, 1), "Confraternização mundial",
            MonthDay.of(4, 21), "Tiradentes",
            MonthDay.of(5, 1), "Dia do trabalho",
            MonthDay.of(9, 7), "Independência do Brasil",
            MonthDay.of(10, 12), "Nossa Senhora Aparecida",
            MonthDay.of(11, 2), "Finados",
            MonthDay.of(11, 15), "Proclamação da República",
            MonthDay.of(11, 20), "Dia da consciência negra",
            MonthDay.of(12, 25), "Natal");

    private static final Map<Integer, List<Holiday>> MOVABLE = Map.of(
            2026, List.of(
                    new Holiday(LocalDate.of(2026, 2, 16), "Carnaval"),
                    new Holiday(LocalDate.of(2026, 2, 17), "Carnaval"),
                    new Holiday(LocalDate.of(2026, 4, 3), "Sexta-feira Santa"),
                    new Holiday(LocalDate.of(2026, 4, 5), "Páscoa"),
                    new Holiday(LocalDate.of(2026, 6, 4), "Corpus Christi")),
            2027, List.of(
                    new Holiday(LocalDate.of(2027, 2, 8), "Carnaval"),
                    new Holiday(LocalDate.of(2027, 2, 9), "Carnaval"),
                    new Holiday(LocalDate.of(2027, 3, 26), "Sexta-feira Santa"),
                    new Holiday(LocalDate.of(2027, 3, 28), "Páscoa"),
                    new Holiday(LocalDate.of(2027, 5, 27), "Corpus Christi")));

    private volatile boolean failing;

    public void failing(boolean failing) {
        this.failing = failing;
    }

    @Override
    public List<Holiday> fetch(int year) {
        if (failing) {
            throw new HolidaySourceException("BrasilAPI simulada fora do ar", null);
        }
        List<Holiday> holidays = new ArrayList<>(MOVABLE.getOrDefault(year, List.of()));
        FIXED.forEach((day, name) -> holidays.add(new Holiday(day.atYear(year), name)));
        holidays.sort((a, b) -> a.date().compareTo(b.date()));
        return holidays;
    }
}
