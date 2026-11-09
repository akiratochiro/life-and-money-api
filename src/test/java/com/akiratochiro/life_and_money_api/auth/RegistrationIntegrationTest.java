package com.akiratochiro.life_and_money_api.auth;

import com.akiratochiro.life_and_money_api.IntegrationTest;
import com.akiratochiro.life_and_money_api.email.EmailMessage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RegistrationIntegrationTest extends IntegrationTest {

    private static final String INVALID_LINK =
            "Este link de confirmação é inválido ou expirou. Faça o cadastro de novo para receber outro.";

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void theAccountOnlyExistsAfterTheEmailedLinkIsUsed() throws Exception {
        requestRegistration("Ana Souza", "cadastro.fluxo@test.dev", "senha12345")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value(
                        "Enviamos um link para o seu e-mail. Abra-o em até 24 horas para ativar a conta."));

        EmailMessage email = emails.awaitMessageTo("cadastro.fluxo@test.dev", 1);
        assertThat(email.subject()).isEqualTo(RegistrationEmails.CONFIRMATION_SUBJECT);
        assertThat(email.text()).contains("Falta um passo, Ana!");

        // Antes de confirmar, não há conta para entrar.
        login("cadastro.fluxo@test.dev", "senha12345").andExpect(status().isUnauthorized());

        confirmEmail(confirmationTokenFor("cadastro.fluxo@test.dev"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Ana Souza"))
                .andExpect(jsonPath("$.email").value("cadastro.fluxo@test.dev"));

        login("cadastro.fluxo@test.dev", "senha12345").andExpect(status().isOk());
    }

    @Test
    void theConfirmedAccountStartsWithTheDefaultCategories() throws Exception {
        Long userId = register("cadastro.categorias@test.dev");

        mockMvc.perform(get("/categories").with(asUser(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6));
    }

    @Test
    void anEmailThatAlreadyHasAnAccountGetsTheSameAnswerAndTheOwnerIsWarned() throws Exception {
        register("cadastro.existe@test.dev");
        emails.clear();

        MvcResult existing = requestRegistration("Outra Pessoa", "cadastro.existe@test.dev", "outraSenha987").andReturn();
        MvcResult fresh = requestRegistration("Outra Pessoa", "cadastro.novo@test.dev", "outraSenha987").andReturn();

        assertThat(existing.getResponse().getStatus())
                .isEqualTo(fresh.getResponse().getStatus())
                .isEqualTo(202);
        assertThat(existing.getResponse().getContentAsString())
                .isEqualTo(fresh.getResponse().getContentAsString());

        // O dono recebe um aviso, sem link de confirmação, e a senha dele não muda.
        EmailMessage warning = emails.awaitMessageTo("cadastro.existe@test.dev", 1);
        assertThat(warning.subject()).isEqualTo(RegistrationEmails.ALREADY_REGISTERED_SUBJECT);
        assertThat(warning.text()).contains("/forgot-password").doesNotContain("/confirm-email");
        login("cadastro.existe@test.dev", "senha12345").andExpect(status().isOk());
        login("cadastro.existe@test.dev", "outraSenha987").andExpect(status().isUnauthorized());
    }

    @Test
    void theLinkWorksOnlyOnce() throws Exception {
        requestRegistration("Teste", "cadastro.uso.unico@test.dev", "senha12345");
        String token = confirmationTokenFor("cadastro.uso.unico@test.dev");

        confirmEmail(token).andExpect(status().isCreated());
        confirmEmail(token)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Link inválido"))
                .andExpect(jsonPath("$.detail").value(INVALID_LINK));
    }

    @Test
    void theLinkExpiresAfter24Hours() throws Exception {
        requestRegistration("Teste", "cadastro.expirado@test.dev", "senha12345");
        String token = confirmationTokenFor("cadastro.expirado@test.dev");

        clock.advance(Duration.ofHours(24).plusSeconds(1));

        confirmEmail(token).andExpect(status().isBadRequest());
        login("cadastro.expirado@test.dev", "senha12345").andExpect(status().isUnauthorized());
    }

    @Test
    void aSecondRequestWithinAMinuteIsIgnoredAndALaterOneReplacesTheLink() throws Exception {
        requestRegistration("Teste", "cadastro.repetido@test.dev", "senha12345");
        String first = confirmationTokenFor("cadastro.repetido@test.dev");

        requestRegistration("Teste", "cadastro.repetido@test.dev", "senha12345");
        assertThat(emails.messagesTo("cadastro.repetido@test.dev")).hasSize(1);

        clock.advance(RegistrationService.MIN_INTERVAL.plusSeconds(1));
        requestRegistration("Teste Novo", "cadastro.repetido@test.dev", "senhaNova987");
        String second = confirmationTokenFor("cadastro.repetido@test.dev");

        // Só o link mais novo vale, com o nome e a senha do pedido mais novo.
        confirmEmail(first).andExpect(status().isBadRequest());
        confirmEmail(second).andExpect(status().isCreated()).andExpect(jsonPath("$.name").value("Teste Novo"));
        login("cadastro.repetido@test.dev", "senhaNova987").andExpect(status().isOk());
    }

    @Test
    void theEmailIsNormalizedLikeEverywhereElse() throws Exception {
        requestRegistration("Teste", "Cadastro.Maiusculas@Test.DEV", "senha12345");
        confirmEmail(confirmationTokenFor("cadastro.maiusculas@test.dev"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("cadastro.maiusculas@test.dev"));
    }

    @Test
    void onlyTheHashOfTheTokenAndOfThePasswordAreStored() throws Exception {
        requestRegistration("Teste", "cadastro.hash@test.dev", "senha12345");
        String token = confirmationTokenFor("cadastro.hash@test.dev");

        var row = jdbc.queryForMap(
                "select token_hash, password_hash from pending_registrations where email = ?", "cadastro.hash@test.dev");
        assertThat(row.get("token_hash")).isEqualTo(OpaqueTokens.hash(token)).isNotEqualTo(token);
        assertThat((String) row.get("password_hash")).startsWith("{bcrypt}").doesNotContain("senha12345");
    }

    private ResultActions requestRegistration(String name, String email, String password) throws Exception {
        return mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "%s", "email": "%s", "password": "%s"}
                        """.formatted(name, email, password)));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "%s"}
                        """.formatted(email, password)));
    }
}
