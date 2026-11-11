package com.akiratochiro.life_and_money_api.holiday;

import java.time.LocalDate;
import java.time.MonthDay;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Os feriados nacionais calculados aqui, sem rede: o plano B quando a BrasilAPI não responde
 * (decisão 25). São os mesmos que ela devolve, com os mesmos nomes:
 * <ul>
 *   <li>9 datas fixas; a Consciência Negra (20/11) é nacional desde 2024 (Lei 14.759/2023);</li>
 *   <li>5 móveis, a partir da Páscoa: Carnaval (segunda e terça, 48 e 47 dias antes), Sexta-feira
 *       Santa (2 dias antes), a própria Páscoa e Corpus Christi (60 dias depois). Carnaval e Corpus
 *       Christi são pontos facultativos, mas os bancos não abrem, por isso contam (decisão 11).</li>
 * </ul>
 */
public final class LocalHolidays {

    private record Fixed(MonthDay day, String name, int sinceYear) {
    }

    private static final List<Fixed> FIXED = List.of(
            new Fixed(MonthDay.of(1, 1), "Confraternização mundial", 0),
            new Fixed(MonthDay.of(4, 21), "Tiradentes", 0),
            new Fixed(MonthDay.of(5, 1), "Dia do trabalho", 0),
            new Fixed(MonthDay.of(9, 7), "Independência do Brasil", 0),
            new Fixed(MonthDay.of(10, 12), "Nossa Senhora Aparecida", 0),
            new Fixed(MonthDay.of(11, 2), "Finados", 0),
            new Fixed(MonthDay.of(11, 15), "Proclamação da República", 0),
            new Fixed(MonthDay.of(11, 20), "Dia da consciência negra", 2024),
            new Fixed(MonthDay.of(12, 25), "Natal", 0));

    private LocalHolidays() {
    }

    public static List<Holiday> of(int year) {
        List<Holiday> holidays = new ArrayList<>();
        for (Fixed fixed : FIXED) {
            if (year >= fixed.sinceYear()) {
                holidays.add(new Holiday(fixed.day().atYear(year), fixed.name()));
            }
        }
        LocalDate easter = easter(year);
        holidays.add(new Holiday(easter.minusDays(48), "Carnaval"));
        holidays.add(new Holiday(easter.minusDays(47), "Carnaval"));
        holidays.add(new Holiday(easter.minusDays(2), "Sexta-feira Santa"));
        holidays.add(new Holiday(easter, "Páscoa"));
        holidays.add(new Holiday(easter.plusDays(60), "Corpus Christi"));
        holidays.sort(Comparator.comparing(Holiday::date));
        return holidays;
    }

    /**
     * O domingo de Páscoa no calendário gregoriano, pelo algoritmo de Meeus/Jones/Butcher: só
     * aritmética inteira, válido para qualquer ano a partir de 1583.
     */
    static LocalDate easter(int year) {
        int a = year % 19;
        int b = year / 100;
        int c = year % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int month = (h + l - 7 * m + 114) / 31;
        int day = ((h + l - 7 * m + 114) % 31) + 1;
        return LocalDate.of(year, month, day);
    }
}
