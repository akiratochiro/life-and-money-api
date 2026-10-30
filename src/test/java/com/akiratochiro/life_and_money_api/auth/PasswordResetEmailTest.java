package com.akiratochiro.life_and_money_api.auth;

import com.akiratochiro.life_and_money_api.email.EmailMessage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordResetEmailTest {

    private static final String LINK = "https://front.exemplo.dev/reset-password?token=abc_DEF-123";

    @Test
    void greetsByFirstNameAndCarriesTheLinkInBothVersions() {
        EmailMessage email = PasswordResetEmail.compose("ana@exemplo.dev", "Ana Souza", LINK);

        assertThat(email.to()).isEqualTo("ana@exemplo.dev");
        assertThat(email.html()).contains("Vamos criar uma senha nova, Ana?", "href=\"" + LINK + "\"");
        assertThat(email.text()).contains("Vamos criar uma senha nova, Ana?", LINK);
    }

    @Test
    void aNameTypedByTheUserCannotInjectHtml() {
        EmailMessage email = PasswordResetEmail.compose("x@exemplo.dev", "<script>alert(1)</script>", LINK);

        assertThat(email.html())
                .doesNotContain("<script>")
                .contains("&lt;script&gt;");
    }
}
