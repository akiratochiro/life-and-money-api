package com.akiratochiro.life_and_money_api.auth;

import com.akiratochiro.life_and_money_api.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RefreshTokenIntegrationTest extends IntegrationTest {

    private static final String REFRESH_HEADER = "X-Refresh-Request";
    private static final String COOKIE = "refreshToken";

    @Test
    void loginReturnsAnAccessTokenThatOpensTheProtectedEndpoints() throws Exception {
        register("sessao.login@test.dev");
        MvcResult login = login("sessao.login@test.dev");

        String accessToken = JsonPath.parse(login.getResponse().getContentAsString())
                .read("$.accessToken", String.class);

        // o token passa pelo filtro e pelo decoder de verdade, não por um mock
        mockMvc.perform(get("/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("sessao.login@test.dev"));
    }

    @Test
    void theRefreshCookieIsHttpOnlyAndLimitedToTheAuthPath() throws Exception {
        register("sessao.cookie@test.dev");

        String setCookie = login("sessao.cookie@test.dev").getResponse()
                .getHeader(HttpHeaders.SET_COOKIE);

        assertThat(setCookie)
                .as("o JavaScript da página não pode ler este cookie")
                .contains("HttpOnly")
                .as("o cookie só viaja nas chamadas de autenticação")
                .contains("Path=/auth");
    }

    @Test
    void refreshingRotatesTheTokenAndReturnsANewAccessToken() throws Exception {
        register("sessao.rotacao@test.dev");
        String first = refreshTokenOf(login("sessao.rotacao@test.dev"));

        MvcResult refreshed = mockMvc.perform(post("/auth/refresh")
                        .cookie(new Cookie(COOKIE, first))
                        .header(REFRESH_HEADER, "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(header().exists(HttpHeaders.SET_COOKIE))
                .andReturn();

        assertThat(refreshTokenOf(refreshed))
                .as("cada uso emite um token novo")
                .isNotEqualTo(first);
    }

    @Test
    void reusingATokenThatWasAlreadyRotatedKillsEverySessionOfTheUser() throws Exception {
        register("sessao.reuso@test.dev");
        String first = refreshTokenOf(login("sessao.reuso@test.dev"));
        String second = refreshTokenOf(refresh(first));

        // o primeiro token já foi usado: só uma cópia vazada explicaria ele voltar
        mockMvc.perform(post("/auth/refresh")
                        .cookie(new Cookie(COOKIE, first))
                        .header(REFRESH_HEADER, "1"))
                .andExpect(status().isUnauthorized());

        // e por precaução o token legítimo também cai: o usuário entra de novo
        mockMvc.perform(post("/auth/refresh")
                        .cookie(new Cookie(COOKIE, second))
                        .header(REFRESH_HEADER, "1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void twoDevicesHaveIndependentSessions() throws Exception {
        register("sessao.dois.aparelhos@test.dev");
        String phone = refreshTokenOf(login("sessao.dois.aparelhos@test.dev"));
        String laptop = refreshTokenOf(login("sessao.dois.aparelhos@test.dev"));

        assertThat(phone).isNotEqualTo(laptop);

        // renovar num aparelho não desconecta o outro
        refresh(phone);
        mockMvc.perform(post("/auth/refresh")
                        .cookie(new Cookie(COOKIE, laptop))
                        .header(REFRESH_HEADER, "1"))
                .andExpect(status().isOk());
    }

    @Test
    void refreshingWithoutTheGuardHeaderIsRejected() throws Exception {
        register("sessao.sem.header@test.dev");
        String token = refreshTokenOf(login("sessao.sem.header@test.dev"));

        // é o que um POST forjado por outro site conseguiria montar
        mockMvc.perform(post("/auth/refresh").cookie(new Cookie(COOKIE, token)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshingWithoutACookieIsRejected() throws Exception {
        mockMvc.perform(post("/auth/refresh").header(REFRESH_HEADER, "1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Sua sessão expirou. Entre de novo."));
    }

    @Test
    void logoutRevokesTheTokenAndClearsTheCookie() throws Exception {
        register("sessao.logout@test.dev");
        String token = refreshTokenOf(login("sessao.logout@test.dev"));

        MvcResult logout = mockMvc.perform(post("/auth/logout").cookie(new Cookie(COOKIE, token)))
                .andExpect(status().isNoContent())
                .andReturn();

        assertThat(logout.getResponse().getHeader(HttpHeaders.SET_COOKIE))
                .as("cookie vazio e já expirado é como o navegador apaga um cookie")
                .contains("Max-Age=0");

        mockMvc.perform(post("/auth/refresh")
                        .cookie(new Cookie(COOKIE, token))
                        .header(REFRESH_HEADER, "1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutWithoutASessionIsNotAnError() throws Exception {
        mockMvc.perform(post("/auth/logout"))
                .andExpect(status().isNoContent());
    }

    private MvcResult login(String email) throws Exception {
        String body = """
                {"email": "%s", "password": "senha12345"}
                """.formatted(email);

        return mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
    }

    private MvcResult refresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/auth/refresh")
                        .cookie(new Cookie(COOKIE, refreshToken))
                        .header(REFRESH_HEADER, "1"))
                .andExpect(status().isOk())
                .andReturn();
    }

    /** Lê o valor do refresh token no header Set-Cookie da resposta. */
    private static String refreshTokenOf(MvcResult result) {
        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).isNotNull().startsWith(COOKIE + "=");

        String withoutName = setCookie.substring((COOKIE + "=").length());
        int end = withoutName.indexOf(';');
        return end < 0 ? withoutName : withoutName.substring(0, end);
    }
}
