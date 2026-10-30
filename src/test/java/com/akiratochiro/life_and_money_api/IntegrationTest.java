package com.akiratochiro.life_and_money_api;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base dos testes de integração: sobe o contexto uma única vez (as anotações são iguais para
 * todas as subclasses, então o Spring reaproveita o mesmo contexto em cache) contra um
 * PostgreSQL de verdade em container, e concentra os atalhos repetidos em todos os cenários.
 */
@SpringBootTest(properties = {
        "jwt.secret=46N3Mn7ipRBtI+LDENQDLRM2mXOGfeamGMqAcgFS9Gc=",
        "app.email.provider=log",
        "app.front-url=http://localhost:5173"
})
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestSupportConfiguration.class})
public abstract class IntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected MutableClock clock;

    @Autowired
    protected RecordingEmailSender emails;

    /** O contexto é compartilhado entre as classes: o que um teste mexeu não vaza para o próximo. */
    @AfterEach
    void resetTestDoubles() {
        clock.reset();
        emails.clear();
    }

    /** Cadastra um usuário e devolve o id dele. */
    protected Long register(String email) throws Exception {
        String body = """
                {"name": "Teste", "email": "%s", "password": "senha12345"}
                """.formatted(email);

        MvcResult result = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        return idOf(result);
    }

    /** Cria uma categoria para o usuário e devolve o id dela. */
    protected Long createCategory(Long userId, String name, String type) throws Exception {
        String body = """
                {"name": "%s", "type": "%s"}
                """.formatted(name, type);

        MvcResult result = mockMvc.perform(post("/categories")
                        .with(asUser(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        return idOf(result);
    }

    /**
     * Autentica a requisição como o usuário informado. O token não é assinado de verdade: o
     * {@code jwt()} do spring-security-test injeta direto o principal que o filtro produziria,
     * então o teste exercita as regras de autorização sem depender da geração do token.
     */
    protected static RequestPostProcessor asUser(Long userId) {
        return jwt().jwt(token -> token.subject(String.valueOf(userId)));
    }

    protected static Long idOf(MvcResult result) throws Exception {
        return JsonPath.parse(result.getResponse().getContentAsString()).read("$.id", Long.class);
    }
}
