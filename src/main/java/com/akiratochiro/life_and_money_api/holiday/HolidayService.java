package com.akiratochiro.life_and_money_api.holiday;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Os feriados nacionais, sem nunca derrubar quem pergunta: se a fonte falhar, a resposta diz
 * que os feriados não estão disponíveis, e as contas seguem só com os fins de semana.
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
            return new HolidaysResponse(year, false, List.of());
        }
        try {
            return new HolidaysResponse(year, true, source.fetch(year));
        } catch (HolidaySourceException e) {
            log.warn("Feriados de {} indisponíveis; os prazos vão considerar só os fins de semana", year, e);
            return new HolidaysResponse(year, false, List.of());
        }
    }

    /** O calendário de dias úteis dos anos {@code firstYear} a {@code lastYear}, inclusive. */
    public HolidayCalendar calendarFor(int firstYear, int lastYear) {
        List<Holiday> holidays = new ArrayList<>();
        boolean complete = true;
        for (int year = firstYear; year <= lastYear; year++) {
            HolidaysResponse response = holidaysOf(year);
            holidays.addAll(response.holidays());
            complete &= response.available();
        }
        return new HolidayCalendar(holidays, complete);
    }
}
