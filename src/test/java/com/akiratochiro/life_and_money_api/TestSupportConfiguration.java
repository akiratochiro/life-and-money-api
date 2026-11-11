package com.akiratochiro.life_and_money_api;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;

import java.time.ZoneId;

/**
 * Substitui o relógio, o envio de e-mail, os feriados, a consulta de CNPJ e a lista de senhas vazadas nos testes de integração. Os nomes dos beans são
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

    /** Os feriados vêm de uma lista fixa: nenhum teste depende da BrasilAPI no ar. */
    @Bean
    @Primary
    FixedHolidaySource fixedHolidaySource() {
        return new FixedHolidaySource();
    }

    /** Os CNPJs vêm de uma lista fixa: nenhum teste depende da BrasilAPI no ar. */
    @Bean
    @Primary
    FixedCnpjLookup fixedCnpjLookup() {
        return new FixedCnpjLookup();
    }

    /** As senhas vazadas vêm de uma lista fixa: nenhum teste depende da Pwned Passwords no ar. */
    @Bean
    @Primary
    KnownBreachedPasswords knownBreachedPasswords() {
        return new KnownBreachedPasswords();
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
