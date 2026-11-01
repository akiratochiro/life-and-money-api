package com.akiratochiro.life_and_money_api.holiday;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * O {@code @Cacheable} só funciona através do proxy do Spring, então este teste sobe um contexto
 * mínimo: o cache e a fonte da BrasilAPI, com as respostas simuladas.
 */
@SpringJUnitConfig(HolidayCacheTest.Config.class)
class HolidayCacheTest {

    private static final String BASE_URL = "https://brasilapi.test/api/feriados/v1";
    private static final String BODY = """
            [{"date":"2026-11-02","name":"Finados","type":"national"}]
            """;

    @Configuration
    @EnableCaching
    static class Config {
        @Bean
        RestClient.Builder builder() {
            return RestClient.builder().baseUrl(BASE_URL);
        }

        @Bean
        MockRestServiceServer server(RestClient.Builder builder) {
            return MockRestServiceServer.bindTo(builder).build();
        }

        @Bean
        HolidaySource source(RestClient.Builder builder, MockRestServiceServer server) {
            return new BrasilApiHolidaySource(builder.build());
        }

        @Bean
        CacheManager cacheManager() {
            return new CaffeineCacheManager("holidays");
        }
    }

    @Autowired
    private HolidaySource source;

    @Autowired
    private MockRestServiceServer server;

    @Autowired
    private CacheManager cacheManager;

    @AfterEach
    void clear() {
        server.reset();
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
    }

    @Test
    void theSameYearIsFetchedOnlyOnce() {
        server.expect(once(), requestTo(BASE_URL + "/2026")).andRespond(withSuccess(BODY, MediaType.APPLICATION_JSON));

        assertThat(source.fetch(2026)).hasSize(1);
        assertThat(source.fetch(2026)).hasSize(1);

        server.verify();
    }

    @Test
    void eachYearHasItsOwnEntry() {
        server.expect(once(), requestTo(BASE_URL + "/2026")).andRespond(withSuccess(BODY, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(BASE_URL + "/2027")).andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        source.fetch(2026);
        source.fetch(2027);
        source.fetch(2026);

        server.verify();
    }

    @Test
    void aFailureIsNotCachedSoTheNextCallTriesAgain() {
        server.expect(once(), requestTo(BASE_URL + "/2026")).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(once(), requestTo(BASE_URL + "/2026")).andRespond(withSuccess(BODY, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> source.fetch(2026)).isInstanceOf(HolidaySourceException.class);
        assertThat(source.fetch(2026)).hasSize(1);

        server.verify();
    }
}
