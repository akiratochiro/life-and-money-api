package com.akiratochiro.life_and_money_api.holiday;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HolidayCalendarTest {

    private static final HolidayCalendar NOVEMBER_2026 = new HolidayCalendar(List.of(
            new Holiday(LocalDate.of(2026, 11, 2), "Finados"),
            new Holiday(LocalDate.of(2026, 11, 15), "Proclamação da República"),
            new Holiday(LocalDate.of(2026, 11, 20), "Dia da consciência negra")));

    @Test
    void aWeekdayThatIsNotAHolidayIsItsOwnBusinessDay() {
        LocalDate tuesday = LocalDate.of(2026, 11, 10);

        assertThat(NOVEMBER_2026.nextBusinessDayFrom(tuesday)).isEqualTo(tuesday);
    }

    @Test
    void saturdayMovesToMonday() {
        assertThat(NOVEMBER_2026.nextBusinessDayFrom(LocalDate.of(2026, 11, 7))).isEqualTo(LocalDate.of(2026, 11, 9));
    }

    @Test
    void aHolidayOnASundayMovesToMonday() {
        // 15/11/2026, Proclamação da República, cai num domingo
        assertThat(NOVEMBER_2026.nextBusinessDayFrom(LocalDate.of(2026, 11, 15))).isEqualTo(LocalDate.of(2026, 11, 16));
    }

    @Test
    void aFridayHolidaySkipsTheWholeWeekend() {
        // 20/11/2026, Consciência Negra, é uma sexta
        assertThat(NOVEMBER_2026.nextBusinessDayFrom(LocalDate.of(2026, 11, 20))).isEqualTo(LocalDate.of(2026, 11, 23));
    }

    @Test
    void aWeekendFollowedByAHolidaySkipsBoth() {
        // sábado 31/10, domingo 1º/11 e segunda 2/11 (Finados): vence na terça
        assertThat(NOVEMBER_2026.nextBusinessDayFrom(LocalDate.of(2026, 10, 31))).isEqualTo(LocalDate.of(2026, 11, 3));
    }

    @Test
    void theEndOfTheYearMovesIntoTheNextOne() {
        // 31/12/2033 é um sábado; 1º/01/2034 é domingo e feriado: o prazo vai para 2 de janeiro
        HolidayCalendar calendar = new HolidayCalendar(List.of(
                new Holiday(LocalDate.of(2033, 12, 25), "Natal"),
                new Holiday(LocalDate.of(2034, 1, 1), "Confraternização mundial")));

        assertThat(calendar.nextBusinessDayFrom(LocalDate.of(2033, 12, 31))).isEqualTo(LocalDate.of(2034, 1, 2));
    }

    @Test
    void withoutHolidaysOnlyWeekendsCount() {
        HolidayCalendar fallback = new HolidayCalendar(List.of());

        assertThat(fallback.nextBusinessDayFrom(LocalDate.of(2026, 11, 2))).as("Finados vira um dia útil")
                .isEqualTo(LocalDate.of(2026, 11, 2));
        assertThat(fallback.nextBusinessDayFrom(LocalDate.of(2026, 11, 14))).isEqualTo(LocalDate.of(2026, 11, 16));
    }
}
