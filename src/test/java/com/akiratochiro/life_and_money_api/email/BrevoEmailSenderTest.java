package com.akiratochiro.life_and_money_api.email;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class BrevoEmailSenderTest {

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer brevo = MockRestServiceServer.bindTo(builder).build();
    private final BrevoEmailSender sender =
            new BrevoEmailSender(builder, "chave-de-teste", "remetente@exemplo.dev", "Life and Money");

    private final EmailMessage message =
            new EmailMessage("ana@exemplo.dev", "Assunto", "<p>Oi</p>", "Oi");

    @Test
    void sendsThroughTheBrevoHttpApiWithTheApiKey() {
        brevo.expect(requestTo(BrevoEmailSender.ENDPOINT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("api-key", "chave-de-teste"))
                .andExpect(content().json("""
                        {
                          "sender": {"name": "Life and Money", "email": "remetente@exemplo.dev"},
                          "to": [{"email": "ana@exemplo.dev"}],
                          "subject": "Assunto",
                          "htmlContent": "<p>Oi</p>",
                          "textContent": "Oi"
                        }
                        """))
                .andRespond(withSuccess("{\"messageId\": \"<1@relay>\"}", MediaType.APPLICATION_JSON));

        sender.send(message);

        brevo.verify();
    }

    @Test
    void aRejectedEmailBecomesADeliveryError() {
        brevo.expect(requestTo(BrevoEmailSender.ENDPOINT))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> sender.send(message))
                .isInstanceOf(EmailDeliveryException.class);
    }
}
