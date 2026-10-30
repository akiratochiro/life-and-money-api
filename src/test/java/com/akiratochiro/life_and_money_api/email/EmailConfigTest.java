package com.akiratochiro.life_and_money_api.email;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailConfigTest {

    private final EmailConfig config = new EmailConfig();
    private final DefaultListableBeanFactory beans = new DefaultListableBeanFactory();

    private EmailSender senderFor(String provider, MockEnvironment environment) {
        return config.emailSender(provider, environment, beans.getBeanProvider(RestClient.Builder.class));
    }

    @Test
    void theLogProviderOnlyWritesToTheLog() {
        assertThat(senderFor("log", new MockEnvironment())).isInstanceOf(LoggingEmailSender.class);
    }

    @Test
    void theBrevoProviderNeedsAKeyAndASender() {
        MockEnvironment complete = new MockEnvironment()
                .withProperty("app.email.brevo.api-key", "chave")
                .withProperty("app.email.from", "remetente@exemplo.dev")
                .withProperty("app.email.from-name", "Life and Money");

        assertThat(senderFor("brevo", complete)).isInstanceOf(BrevoEmailSender.class);
    }

    @Test
    void anEmptyKeyStopsTheApplicationInsteadOfFailingOnTheFirstEmail() {
        // "${BREVO_API_KEY:}" vira string vazia quando a variável não foi definida.
        MockEnvironment withoutKey = new MockEnvironment()
                .withProperty("app.email.brevo.api-key", "")
                .withProperty("app.email.from", "remetente@exemplo.dev")
                .withProperty("app.email.from-name", "Life and Money");

        assertThatThrownBy(() -> senderFor("brevo", withoutKey))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.email.brevo.api-key");
    }

    @Test
    void anUnknownProviderStopsTheApplication() {
        assertThatThrownBy(() -> senderFor("smtp", new MockEnvironment()))
                .isInstanceOf(IllegalStateException.class);
    }
}
