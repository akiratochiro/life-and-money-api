package com.akiratochiro.life_and_money_api.holiday;

import com.akiratochiro.life_and_money_api.FixedHolidaySource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

class LocalHolidaysTest {

    /** Domingos de Páscoa conhecidos, de 2020 a 2030. */
    @ParameterizedTest
    @CsvSource({"2020, 2020-04-12", "2021, 2021-04-04", "2022, 2022-04-17", "2023, 2023-04-09", "2024, 2024-03-31",
            "2025, 2025-04-20", "2026, 2026-04-05", "2027, 2027-03-28", "2028, 2028-04-16", "2029, 2029-04-01",
            "2030, 2030-04-21"})
    void easterSunday(int year, LocalDate expected) {
        assertThat(LocalHolidays.easter(year)).isEqualTo(expected);
    }

    /** O mesmo que a BrasilAPI devolve (a lista fixa dos testes foi copiada dela para 2026 e 2027). */
    @Test
    void sameAsTheBrasilApiIn2026And2027() {
        FixedHolidaySource brasilApi = new FixedHolidaySource();

        assertThat(LocalHolidays.of(2026)).isEqualTo(brasilApi.fetch(2026));
        assertThat(LocalHolidays.of(2027)).isEqualTo(brasilApi.fetch(2027));
    }

    @Test
    void theNationalBlackConsciousnessDayStartsIn2024() {
        assertThat(LocalHolidays.of(2023)).noneMatch(h -> h.date().equals(LocalDate.of(2023, 11, 20)));
        assertThat(LocalHolidays.of(2024)).anyMatch(h -> h.date().equals(LocalDate.of(2024, 11, 20)));
    }

    @Test
    void thePreviousBusinessDaySkipsWeekendsAndHolidays() {
        HolidayCalendar calendar = new HolidayCalendar(LocalHolidays.of(2026));

        // 20/11/2026 é sexta e feriado: o dia útil anterior é quinta 19/11.
        assertThat(calendar.previousBusinessDayFrom(LocalDate.of(2026, 11, 20))).isEqualTo(LocalDate.of(2026, 11, 19));
        // 20/06/2026 é sábado: sexta 19/06.
        assertThat(calendar.previousBusinessDayFrom(YearMonth.of(2026, 6).atDay(20))).isEqualTo(LocalDate.of(2026, 6, 19));
        assertThat(calendar.previousBusinessDayFrom(LocalDate.of(2026, 11, 18))).isEqualTo(LocalDate.of(2026, 11, 18));
    }
}
