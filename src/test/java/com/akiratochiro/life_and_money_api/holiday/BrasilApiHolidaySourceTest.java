package com.akiratochiro.life_and_money_api.holiday;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** O cliente da BrasilAPI contra respostas simuladas: o formato real, erros e falta de resposta. */
class BrasilApiHolidaySourceTest {

    private static final String BASE_URL = "https://brasilapi.test/api/feriados/v1";

    private MockRestServiceServer server;
    private BrasilApiHolidaySource source;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        source = new BrasilApiHolidaySource(builder.build());
    }

    @Test
    void readsTheDatesAndNamesAndIgnoresTheOtherFields() {
        // given: um trecho da resposta real de 2026
        server.expect(requestTo(BASE_URL + "/2026"))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andRespond(withSuccess("""
                        [{"date":"2026-02-16","name":"Carnaval","type":"national","weekday":"segunda-feira"},
                         {"date":"2026-11-02","name":"Finados","type":"national","weekday":"segunda-feira"},
                         {"date":"2026-11-20","name":"Dia da consciência negra","type":"national","weekday":"sexta-feira"}]
                        """, MediaType.APPLICATION_JSON));

        // when
        List<Holiday> holidays = source.fetch(2026);

        // then
        assertThat(holidays).containsExactly(
                new Holiday(LocalDate.of(2026, 2, 16), "Carnaval"),
                new Holiday(LocalDate.of(2026, 11, 2), "Finados"),
                new Holiday(LocalDate.of(2026, 11, 20), "Dia da consciência negra"));
        server.verify();
    }

    @Test
    void aServerErrorBecomesAHolidaySourceException() {
        server.expect(requestTo(BASE_URL + "/2026")).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> source.fetch(2026))
                .isInstanceOf(HolidaySourceException.class)
                .hasMessageContaining("2026");
    }

    @Test
    void aYearOutsideTheRangeBecomesAHolidaySourceException() {
        server.expect(requestTo(BASE_URL + "/1800")).andRespond(withStatus(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"message":"Ano fora do intervalo suportado entre 1900 e 2199.","type":"feriados_range_error"}
                        """));

        assertThatThrownBy(() -> source.fetch(1800)).isInstanceOf(HolidaySourceException.class);
    }

    @Test
    void noAnswerInTimeBecomesAHolidaySourceException() {
        server.expect(requestTo(BASE_URL + "/2026")).andRespond(withException(new SocketTimeoutException("read timed out")));

        assertThatThrownBy(() -> source.fetch(2026)).isInstanceOf(HolidaySourceException.class);
    }

    @Test
    void anInvalidDateIsRejected() {
        server.expect(requestTo(BASE_URL + "/2026")).andRespond(withSuccess("""
                [{"date":"16/02/2026","name":"Carnaval"}]
                """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> source.fetch(2026)).isInstanceOf(HolidaySourceException.class);
    }

    @Test
    void aHolidayWithoutNameIsRejected() {
        server.expect(requestTo(BASE_URL + "/2026")).andRespond(withSuccess("""
                [{"date":"2026-02-16"}]
                """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> source.fetch(2026))
                .isInstanceOf(HolidaySourceException.class)
                .hasMessageContaining("sem data ou sem nome");
    }

    @Test
    void anEmptyBodyIsRejected() {
        server.expect(requestTo(BASE_URL + "/2026")).andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> source.fetch(2026)).isInstanceOf(HolidaySourceException.class);
    }
}
