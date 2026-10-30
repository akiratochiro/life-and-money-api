package com.akiratochiro.life_and_money_api;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;

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

    /**
     * O {@code @Async} roda na própria thread do teste. Assim um teste consegue afirmar que algo
     * <em>não</em> aconteceu (nenhum e-mail saiu) sem esperar um tempo arbitrário e torcer.
     */
    @Bean
    TaskExecutor taskExecutor() {
        return new SyncTaskExecutor();
    }
}
