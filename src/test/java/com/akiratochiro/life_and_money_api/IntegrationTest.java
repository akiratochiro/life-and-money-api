package com.akiratochiro.life_and_money_api;

import com.akiratochiro.life_and_money_api.email.EmailMessage;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

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
        "app.front-url=http://localhost:5173",
        "app.client-ip.proxy-secret=segredo-dos-testes",
        // Os outros testes fazem dezenas de logins e cadastros do mesmo IP; os limites têm
        // os testes deles, num contexto à parte (RateLimitIntegrationTest).
        "app.rate-limit.enabled=false"
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

    @Autowired
    protected FixedHolidaySource holidays;

    @Autowired
    protected FixedCnpjLookup cnpjs;

    /** O contexto é compartilhado entre as classes: o que um teste mexeu não vaza para o próximo. */
    @AfterEach
    void resetTestDoubles() {
        clock.reset();
        emails.clear();
        holidays.failing(false);
        cnpjs.failing(false);
    }

    private static final Pattern CONFIRMATION_LINK = Pattern.compile("/confirm-email\\?token=([A-Za-z0-9_-]+)");

    /**
     * Cadastra um usuário como um usuário de verdade faria (pede o cadastro e usa o link do e-mail)
     * e devolve o id dele.
     */
    protected Long register(String email) throws Exception {
        String body = """
                {"name": "Teste", "email": "%s", "password": "senha12345"}
                """.formatted(email);

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted());

        MvcResult result = confirmEmail(confirmationTokenFor(email))
                .andExpect(status().isCreated())
                .andReturn();

        // O usuário chega pronto, como antes da confirmação existir: a caixa dele começa vazia, e o
        // "primeiro e-mail" que um teste esperar é o do que ele estiver testando.
        emails.clearTo(email);
        return idOf(result);
    }

    protected ResultActions confirmEmail(String token) throws Exception {
        return mockMvc.perform(post("/auth/confirm-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"token": "%s"}
                        """.formatted(token)));
    }

    /** O token do último link de confirmação enviado para o e-mail. */
    protected String confirmationTokenFor(String email) {
        List<EmailMessage> messages = emails.messagesTo(email);
        assertThat(messages).as("um e-mail de confirmação para " + email).isNotEmpty();
        Matcher matcher = CONFIRMATION_LINK.matcher(messages.getLast().text());
        assertThat(matcher.find()).as("o e-mail traz o link de confirmação").isTrue();
        return matcher.group(1);
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
