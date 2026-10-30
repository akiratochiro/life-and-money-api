package com.akiratochiro.life_and_money_api.auth;

import com.akiratochiro.life_and_money_api.IntegrationTest;
import com.akiratochiro.life_and_money_api.email.EmailMessage;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PasswordResetIntegrationTest extends IntegrationTest {

    private static final Pattern TOKEN_IN_LINK = Pattern.compile("/reset-password\\?token=([A-Za-z0-9_-]+)");
    private static final String INVALID_LINK =
            "Este link para criar uma senha nova é inválido ou expirou. Peça outro.";

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void anUnknownEmailGetsExactlyTheSameAnswerAsAnExistingOne() throws Exception {
        register("reset.existe@test.dev");

        MvcResult existing = forgotPassword("reset.existe@test.dev").andReturn();
        MvcResult unknown = forgotPassword("reset.ninguem@test.dev").andReturn();

        assertThat(unknown.getResponse().getStatus())
                .isEqualTo(existing.getResponse().getStatus())
                .isEqualTo(202);
        assertThat(unknown.getResponse().getContentAsString())
                .isEqualTo(existing.getResponse().getContentAsString());

        emails.awaitMessageTo("reset.existe@test.dev", 1);
        assertThat(emails.messagesTo("reset.ninguem@test.dev")).isEmpty();
    }

    @Test
    void theEmailedLinkCreatesANewPasswordAndEndsEverySession() throws Exception {
        register("reset.fluxo@test.dev");
        String session = refreshCookieOf(login("reset.fluxo@test.dev", "senha12345").andReturn());

        forgotPassword("reset.fluxo@test.dev");
        String token = tokenFrom(emails.awaitMessageTo("reset.fluxo@test.dev", 1));

        resetPassword(token, "senhaNova987").andExpect(status().isNoContent());

        login("reset.fluxo@test.dev", "senhaNova987").andExpect(status().isOk());
        login("reset.fluxo@test.dev", "senha12345").andExpect(status().isUnauthorized());
        mockMvc.perform(post("/auth/refresh")
                        .cookie(new Cookie(RefreshTokenCookie.NAME, session))
                        .header("X-Refresh-Request", "1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anExpiredTokenIsRefused() throws Exception {
        register("reset.expirado@test.dev");
        forgotPassword("reset.expirado@test.dev");
        String token = tokenFrom(emails.awaitMessageTo("reset.expirado@test.dev", 1));

        clock.advance(Duration.ofMinutes(30));

        resetPassword(token, "senhaNova987")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(INVALID_LINK));
        login("reset.expirado@test.dev", "senha12345").andExpect(status().isOk());
    }

    @Test
    void aTokenStillWorksJustBeforeItExpires() throws Exception {
        register("reset.no.limite@test.dev");
        forgotPassword("reset.no.limite@test.dev");
        String token = tokenFrom(emails.awaitMessageTo("reset.no.limite@test.dev", 1));

        clock.advance(Duration.ofMinutes(29));

        resetPassword(token, "senhaNova987").andExpect(status().isNoContent());
    }

    @Test
    void aTokenWorksOnlyOnce() throws Exception {
        register("reset.reuso@test.dev");
        forgotPassword("reset.reuso@test.dev");
        String token = tokenFrom(emails.awaitMessageTo("reset.reuso@test.dev", 1));

        resetPassword(token, "senhaNova987").andExpect(status().isNoContent());

        resetPassword(token, "outraSenha123")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(INVALID_LINK));
        login("reset.reuso@test.dev", "senhaNova987").andExpect(status().isOk());
    }

    @Test
    void anUnknownTokenIsRefusedWithTheSameMessage() throws Exception {
        resetPassword(OpaqueTokens.generate(), "senhaNova987")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(INVALID_LINK));
    }

    @Test
    void aNewRequestInvalidatesTheLinksSentBefore() throws Exception {
        register("reset.novo.pedido@test.dev");
        forgotPassword("reset.novo.pedido@test.dev");
        String first = tokenFrom(emails.awaitMessageTo("reset.novo.pedido@test.dev", 1));
        clock.advance(Duration.ofSeconds(61));
        forgotPassword("reset.novo.pedido@test.dev");
        String second = tokenFrom(emails.awaitMessageTo("reset.novo.pedido@test.dev", 2));

        resetPassword(first, "senhaNova987").andExpect(status().isBadRequest());
        resetPassword(second, "senhaNova987").andExpect(status().isNoContent());
    }

    @Test
    void aSecondRequestWithinAMinuteSendsNothingAndKeepsTheFirstLinkValid() throws Exception {
        register("reset.intervalo@test.dev");
        MvcResult first = forgotPassword("reset.intervalo@test.dev").andReturn();
        String token = tokenFrom(emails.awaitMessageTo("reset.intervalo@test.dev", 1));

        clock.advance(Duration.ofSeconds(59));
        MvcResult second = forgotPassword("reset.intervalo@test.dev").andReturn();

        // A resposta não muda: um 429 só para quem tem conta revelaria quem tem cadastro.
        assertThat(second.getResponse().getStatus()).isEqualTo(first.getResponse().getStatus());
        assertThat(second.getResponse().getContentAsString())
                .isEqualTo(first.getResponse().getContentAsString());
        assertThat(emails.messagesTo("reset.intervalo@test.dev")).hasSize(1);

        resetPassword(token, "senhaNova987").andExpect(status().isNoContent());
    }

    @Test
    void theIntervalIsPerAccount() throws Exception {
        register("reset.conta.a@test.dev");
        register("reset.conta.b@test.dev");

        forgotPassword("reset.conta.a@test.dev");
        forgotPassword("reset.conta.b@test.dev");

        assertThat(emails.messagesTo("reset.conta.a@test.dev")).hasSize(1);
        assertThat(emails.messagesTo("reset.conta.b@test.dev")).hasSize(1);
    }

    @Test
    void theNewPasswordFollowsTheSameRulesAsRegistration() throws Exception {
        register("reset.regras@test.dev");
        forgotPassword("reset.regras@test.dev");
        String token = tokenFrom(emails.awaitMessageTo("reset.regras@test.dev", 1));

        resetPassword(token, "curta")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.newPassword").value("A senha deve ter entre 8 e 72 caracteres."));

        // Uma senha recusada pela validação não gasta o link.
        resetPassword(token, "senhaNova987").andExpect(status().isNoContent());
    }

    @Test
    void onlyTheHashOfTheTokenIsStored() throws Exception {
        Long userId = register("reset.hash@test.dev");
        forgotPassword("reset.hash@test.dev");
        String token = tokenFrom(emails.awaitMessageTo("reset.hash@test.dev", 1));

        String stored = jdbc.queryForObject(
                "select token_hash from password_reset_tokens where user_id = ?", String.class, userId);

        assertThat(token).hasSize(43);
        assertThat(stored)
                .isNotEqualTo(token)
                .isEqualTo(OpaqueTokens.hash(token))
                .hasSize(64);
    }

    @Test
    void theEmailLinksToTheFrontAndWarnsAboutTheDeadline() throws Exception {
        register("reset.email@test.dev");
        forgotPassword("reset.email@test.dev");

        EmailMessage email = emails.awaitMessageTo("reset.email@test.dev", 1);

        assertThat(email.subject()).isEqualTo(PasswordResetEmail.SUBJECT);
        assertThat(email.text()).contains("http://localhost:5173/reset-password?token=", "30 minutos");
        assertThat(email.html()).contains("http://localhost:5173/reset-password?token=");
    }

    private ResultActions forgotPassword(String email) throws Exception {
        return mockMvc.perform(post("/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s"}
                        """.formatted(email)));
    }

    private ResultActions resetPassword(String token, String newPassword) throws Exception {
        return mockMvc.perform(post("/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"token": "%s", "newPassword": "%s"}
                        """.formatted(token, newPassword)));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "%s"}
                        """.formatted(email, password)));
    }

    private static String refreshCookieOf(MvcResult result) {
        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).isNotNull().startsWith(RefreshTokenCookie.NAME + "=");
        String value = setCookie.substring((RefreshTokenCookie.NAME + "=").length());
        return value.substring(0, value.indexOf(';'));
    }

    private static String tokenFrom(EmailMessage email) {
        Matcher matcher = TOKEN_IN_LINK.matcher(email.text());
        assertThat(matcher.find()).as("o e-mail traz o link com o token").isTrue();
        return matcher.group(1);
    }
}
