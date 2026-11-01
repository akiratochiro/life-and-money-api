package com.akiratochiro.life_and_money_api.holiday;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HolidayServiceTest {

    private static final Holiday FINADOS = new Holiday(LocalDate.of(2026, 11, 2), "Finados");

    @Mock
    private HolidaySource source;

    @InjectMocks
    private HolidayService service;

    @Test
    void returnsTheHolidaysOfTheYear() {
        when(source.fetch(2026)).thenReturn(List.of(FINADOS));

        HolidaysResponse response = service.holidaysOf(2026);

        assertThat(response.available()).isTrue();
        assertThat(response.holidays()).containsExactly(FINADOS);
    }

    @Test
    void aFailingSourceNeverBreaksTheCaller() {
        when(source.fetch(2026)).thenThrow(new HolidaySourceException("fora do ar", null));

        HolidaysResponse response = service.holidaysOf(2026);

        assertThat(response.available()).isFalse();
        assertThat(response.holidays()).isEmpty();
    }

    @Test
    void yearsOutsideTheSupportedRangeAreNotEvenAsked() {
        assertThat(service.holidaysOf(1899).available()).isFalse();
        assertThat(service.holidaysOf(2200).available()).isFalse();
        verify(source, never()).fetch(anyInt());
    }

    @Test
    void aCalendarIsIncompleteIfAnyYearFailed() {
        when(source.fetch(2026)).thenReturn(List.of(FINADOS));
        when(source.fetch(2027)).thenThrow(new HolidaySourceException("fora do ar", null));

        HolidayCalendar calendar = service.calendarFor(2026, 2027);

        assertThat(calendar.isComplete()).isFalse();
        assertThat(calendar.isBusinessDay(FINADOS.date())).as("o ano que carregou continua valendo").isFalse();
    }
}
