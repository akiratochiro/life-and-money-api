package com.akiratochiro.life_and_money_api.config;

import com.akiratochiro.life_and_money_api.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O front e a API ficam na mesma origem: o Worker do front repassa /api/* para cá. Por isso a
 * API não libera CORS para nenhuma origem.
 */
class SameOriginIntegrationTest extends IntegrationTest {

    @Test
    void aPreflightFromAnotherSiteIsNotAllowed() throws Exception {
        mockMvc.perform(options("/auth/refresh")
                        .header(HttpHeaders.ORIGIN, "https://site-malicioso.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "X-Refresh-Request"))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
    }

    @Test
    void aRequestForwardedByTheFrontProxyIsAccepted() throws Exception {
        register("proxy@test.dev");

        // O Worker repassa o Origin do navegador, que é o domínio do front, e não o da API.
        // Para o Spring isso tem cara de requisição cross-origin, e ela não pode ser recusada.
        mockMvc.perform(post("/auth/login")
                        .header(HttpHeaders.ORIGIN, "https://life-and-money-web.exemplo.workers.dev")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "proxy@test.dev", "password": "senha12345"}
                                """))
                .andExpect(status().isOk());
    }
}
