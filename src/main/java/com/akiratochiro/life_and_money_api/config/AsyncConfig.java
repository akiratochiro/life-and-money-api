package com.akiratochiro.life_and_money_api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Liga o {@code @Async}, usado para enviar e-mails fora da thread da requisição. O executor é o
 * que o Spring Boot já configura.
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
