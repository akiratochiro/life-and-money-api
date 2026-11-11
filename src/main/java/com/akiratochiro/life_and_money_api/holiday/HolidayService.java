package com.akiratochiro.life_and_money_api.holiday;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Os feriados nacionais, sem nunca derrubar quem pergunta: se a BrasilAPI falhar, eles são
 * calculados aqui ({@link LocalHolidays}), e os prazos continuam sabendo de Finados e do Carnaval.
 */
@Service
public class HolidayService {

    /** O intervalo que a BrasilAPI cobre; fora dele, ela responde 404 e nem vale perguntar. */
    static final int FIRST_YEAR = 1900;
    static final int LAST_YEAR = 2199;

    private static final Logger log = LoggerFactory.getLogger(HolidayService.class);

    private final HolidaySource source;

    public HolidayService(HolidaySource source) {
        this.source = source;
    }

    public HolidaysResponse holidaysOf(int year) {
        if (year < FIRST_YEAR || year > LAST_YEAR) {
            return new HolidaysResponse(year, false, List.of(), null);
        }
        try {
            return new HolidaysResponse(year, true, source.fetch(year), HolidaysResponse.Source.BRASIL_API);
        } catch (HolidaySourceException e) {
            log.warn("BrasilAPI sem os feriados de {}; usando o cálculo local", year, e);
            return new HolidaysResponse(year, true, LocalHolidays.of(year), HolidaysResponse.Source.LOCAL);
        }
    }

    /** O calendário de dias úteis dos anos {@code firstYear} a {@code lastYear}, inclusive. */
    public HolidayCalendar calendarFor(int firstYear, int lastYear) {
        List<Holiday> holidays = new ArrayList<>();
        for (int year = firstYear; year <= lastYear; year++) {
            holidays.addAll(holidaysOf(year).holidays());
        }
        return new HolidayCalendar(holidays);
    }
}
