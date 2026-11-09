package com.akiratochiro.life_and_money_api.auth;

import com.akiratochiro.life_and_money_api.email.EmailMessage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RegistrationEmailsTest {

    private static final String LINK = "https://front.exemplo.dev/confirm-email?token=abc_DEF-123";

    @Test
    void theConfirmationGreetsByFirstNameAndCarriesTheLinkInBothVersions() {
        EmailMessage email = RegistrationEmails.confirmation("ana@exemplo.dev", "Ana Souza", LINK);

        assertThat(email.to()).isEqualTo("ana@exemplo.dev");
        assertThat(email.html()).contains("Olá, Ana.", "href=\"" + LINK + "\"");
        assertThat(email.text()).contains("Olá, Ana.", LINK, "24 horas", "não é spam");
    }

    @Test
    void itLooksLikeAnOrdinaryEmail() {
        EmailMessage email = RegistrationEmails.confirmation("ana@exemplo.dev", "Ana Souza", LINK);

        // Sem estilos, tabelas, imagens nem botão: só parágrafos e o link por extenso.
        assertThat(email.html()).doesNotContain("style=", "<table", "<img", "background");
        assertThat(email.html()).contains(">" + LINK + "</a>");
        assertThat(email.text()).endsWith("Life and Money\n");
    }

    @Test
    void aNameTypedInTheFormCannotInjectHtml() {
        EmailMessage email = RegistrationEmails.confirmation("x@exemplo.dev", "<img src=x onerror=alert(1)>", LINK);

        assertThat(email.html()).doesNotContain("<img").contains("&lt;img");
    }

    @Test
    void theWarningToTheOwnerPointsToPasswordResetAndNotToAConfirmation() {
        EmailMessage email = RegistrationEmails.alreadyRegistered("ana@exemplo.dev", "https://front.exemplo.dev/forgot-password");

        assertThat(email.text()).contains("já está cadastrado", "/forgot-password").doesNotContain("confirm-email");
    }
}
