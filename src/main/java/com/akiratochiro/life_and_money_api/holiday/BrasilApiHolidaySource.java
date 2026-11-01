package com.akiratochiro.life_and_money_api.holiday;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;

/**
 * Feriados nacionais da BrasilAPI ({@code GET /api/feriados/v1/{ano}}), que os calcula: datas
 * fixas mais as que dependem da Páscoa, de 1900 a 2199. Além dos feriados de lei, ela devolve
 * Carnaval e Corpus Christi, que entram aqui como dias não úteis (banco não abre no Carnaval).
 *
 * <p>O cache é por ano, porque os feriados de um ano não mudam. Uma exceção não entra no cache:
 * se a BrasilAPI falhar, a próxima requisição tenta de novo, em vez de ficar sem feriados até o
 * cache expirar.
 */
class BrasilApiHolidaySource implements HolidaySource {

    private final RestClient restClient;

    BrasilApiHolidaySource(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    @Cacheable("holidays")
    public List<Holiday> fetch(int year) {
        try {
            BrasilApiHoliday[] body = restClient.get()
                    .uri("/{year}", year)
                    .retrieve()
                    .body(BrasilApiHoliday[].class);
            if (body == null) {
                throw new HolidaySourceException("A BrasilAPI respondeu sem corpo para " + year, null);
            }
            return Arrays.stream(body).map(BrasilApiHolidaySource::toHoliday).toList();
        } catch (RestClientException | DateTimeParseException e) {
            throw new HolidaySourceException("Não foi possível obter os feriados de " + year + " na BrasilAPI", e);
        }
    }

    private static Holiday toHoliday(BrasilApiHoliday holiday) {
        if (holiday == null || holiday.date() == null || holiday.name() == null) {
            throw new HolidaySourceException("A BrasilAPI mandou um feriado sem data ou sem nome", null);
        }
        return new Holiday(LocalDate.parse(holiday.date()), holiday.name());
    }

    /** Só os campos usados; os outros ({@code type}, {@code weekday}) são ignorados. */
    record BrasilApiHoliday(String date, String name) {
    }
}
