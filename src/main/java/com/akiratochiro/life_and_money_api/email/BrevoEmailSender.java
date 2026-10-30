package com.akiratochiro.life_and_money_api.email;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

/**
 * Envia pela API HTTP do Brevo. Não por SMTP: o plano gratuito do Render bloqueia a saída nas
 * portas de SMTP (25, 465 e 587), e a API HTTP sai pela 443 como qualquer outra requisição.
 */
class BrevoEmailSender implements EmailSender {

    static final String ENDPOINT = "https://api.brevo.com/v3/smtp/email";

    private final RestClient restClient;
    private final Sender sender;

    BrevoEmailSender(RestClient.Builder builder, String apiKey, String fromEmail, String fromName) {
        this.restClient = builder
                .baseUrl(ENDPOINT)
                .defaultHeader("api-key", apiKey)
                .build();
        this.sender = new Sender(fromName, fromEmail);
    }

    @Override
    public void send(EmailMessage message) {
        try {
            restClient.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new Request(sender, List.of(new Recipient(message.to())),
                            message.subject(), message.html(), message.text()))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            throw new EmailDeliveryException("O Brevo não aceitou o e-mail", e);
        }
    }

    record Sender(String name, String email) {
    }

    record Recipient(String email) {
    }

    record Request(Sender sender, List<Recipient> to, String subject, String htmlContent, String textContent) {
    }
}
