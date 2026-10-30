package com.akiratochiro.life_and_money_api;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.ZoneId;

/**
 * Substitui o relógio e o envio de e-mail nos testes de integração. Os nomes dos beans são
 * diferentes dos originais e o {@code @Primary} decide quem é injetado.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestSupportConfiguration {

    @Bean
    @Primary
    MutableClock testClock() {
        return new MutableClock(ZoneId.of("America/Sao_Paulo"));
    }

    @Bean
    @Primary
    RecordingEmailSender recordingEmailSender() {
        return new RecordingEmailSender();
    }
}
