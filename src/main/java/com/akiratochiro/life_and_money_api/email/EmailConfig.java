package com.akiratochiro.life_and_money_api.email;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.client.RestClient;

/**
 * Escolhe o {@link EmailSender} por {@code app.email.provider}, que não tem valor padrão de
 * propósito: se a variável for esquecida em produção, a API não sobe, em vez de cair no provedor
 * que só registra e gravar links de redefinição de senha no log.
 */
@Configuration
class EmailConfig {

    @Bean
    EmailSender emailSender(@Value("${app.email.provider}") String provider,
                            Environment environment,
                            ObjectProvider<RestClient.Builder> restClientBuilder) {
        return switch (provider) {
            case "log" -> new LoggingEmailSender();
            case "brevo" -> new BrevoEmailSender(
                    restClientBuilder.getIfAvailable(RestClient::builder),
                    required(environment, "app.email.brevo.api-key"),
                    required(environment, "app.email.from"),
                    required(environment, "app.email.from-name"));
            default -> throw new IllegalStateException(
                    "app.email.provider deve ser 'log' ou 'brevo', mas é '" + provider + "'");
        };
    }

    /** Como o getRequiredProperty, mas também recusa o valor vazio que um "${VAR:}" produz. */
    private static String required(Environment environment, String key) {
        String value = environment.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(key + " é obrigatória quando app.email.provider é 'brevo'");
        }
        return value;
    }
}
