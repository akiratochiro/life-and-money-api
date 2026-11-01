package com.akiratochiro.life_and_money_api.holiday;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * O timeout de leitura de verdade, contra um servidor HTTP local que demora a responder. O
 * MockRestServiceServer não serve para isso: ele substitui a camada HTTP inteira, timeout incluído.
 */
class BrasilApiTimeoutTest {

    private HttpServer slowServer;
    /** Segura a resposta até o fim do teste, sem deixar uma thread dormindo depois dele. */
    private final CountDownLatch release = new CountDownLatch(1);

    @BeforeEach
    void startSlowServer() throws IOException {
        slowServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        slowServer.createContext("/", exchange -> {
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        slowServer.start();
    }

    @AfterEach
    void stopSlowServer() {
        release.countDown();
        slowServer.stop(0);
    }

    @Test
    void aSlowBrasilApiIsGivenUpOnAfterTheReadTimeout() {
        // given: o mesmo cliente da produção, com um timeout de leitura de 300 ms
        String baseUrl = "http://127.0.0.1:" + slowServer.getAddress().getPort();
        BrasilApiHolidaySource source = HolidayConfig.brasilApi(
                RestClient.builder(), baseUrl, Duration.ofSeconds(1), Duration.ofMillis(300));

        // when / then: desiste em bem menos do que os 5 s que o servidor levaria
        long start = System.nanoTime();
        assertThatThrownBy(() -> source.fetch(2026)).isInstanceOf(HolidaySourceException.class);
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(2));
    }
}
