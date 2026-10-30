package com.akiratochiro.life_and_money_api.ratelimit;

import com.akiratochiro.life_and_money_api.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Duration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Os limites ligados de verdade. Cada teste usa IPs próprios, então os baldes de um não
 * interferem nos de outro, mesmo com o contexto (e o RateLimiter) compartilhado.
 */
@TestPropertySource(properties = "app.rate-limit.enabled=true")
class RateLimitIntegrationTest extends IntegrationTest {

    private static final String PROXY_SECRET = "segredo-dos-testes";

    /**
     * Relógio parado: com ele andando, uma tentativa poderia voltar ao balde no meio do teste, e o
     * tempo de espera esperado deixaria de ser exato.
     */
    @BeforeEach
    void freezeClock() {
        clock.advance(Duration.ZERO);
    }

    @Test
    void theLoginIsLimitedPerIpAndAnswers429WithRetryAfter() throws Exception {
        for (int attempt = 1; attempt <= 20; attempt++) {
            login(fromCloudflare("203.0.113.10"), "ninguem" + attempt + "@test.dev")
                    .andExpect(status().isUnauthorized());
        }

        login(fromCloudflare("203.0.113.10"), "ninguem21@test.dev")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "3"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.title").value("Muitas tentativas"))
                .andExpect(jsonPath("$.detail").value("Muitas tentativas em pouco tempo. Tente de novo em 3 segundos."))
                .andExpect(jsonPath("$.retryAfterSeconds").value(3));

        // Outro IP segue com o balde dele cheio.
        login(fromCloudflare("203.0.113.11"), "ninguem22@test.dev").andExpect(status().isUnauthorized());
    }

    @Test
    void theLoginIsAlsoLimitedPerEmailEvenFromManyIps() throws Exception {
        for (int attempt = 1; attempt <= 10; attempt++) {
            login(fromWorker("203.0.113." + (20 + attempt)), "Alvo@Test.dev")
                    .andExpect(status().isUnauthorized());
        }

        // Outro IP, mesmo e-mail com outras maiúsculas: conta no mesmo balde.
        login(fromWorker("203.0.113.40"), "alvo@test.dev")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.detail").value("Muitas tentativas em pouco tempo. Tente de novo em 2 minutos."));
    }

    @Test
    void attemptsComeBackAsTimePasses() throws Exception {
        for (int attempt = 1; attempt <= 20; attempt++) {
            login(fromCloudflare("203.0.113.50"), "volta" + attempt + "@test.dev");
        }
        login(fromCloudflare("203.0.113.50"), "volta21@test.dev").andExpect(status().isTooManyRequests());

        clock.advance(Duration.ofSeconds(3));

        login(fromCloudflare("203.0.113.50"), "volta22@test.dev").andExpect(status().isUnauthorized());
    }

    @Test
    void aForgedClientIpWithoutTheProxySecretDoesNotEscapeTheLimit() throws Exception {
        // Quem chama o Render direto e inventa um X-Client-IP diferente a cada tentativa continua
        // sendo contado pelo IP real dele (o CF-Connecting-IP, que a Cloudflare escreve).
        for (int attempt = 1; attempt <= 20; attempt++) {
            login(fromCloudflare("203.0.113.60").header("X-Client-IP", "198.51.100." + attempt),
                    "forjado" + attempt + "@test.dev");
        }

        login(fromCloudflare("203.0.113.60").header("X-Client-IP", "198.51.100.99"), "forjado21@test.dev")
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void usersBehindTheWorkerDoNotShareOneLimit() throws Exception {
        // Atrás do Worker, o CF-Connecting-IP é o mesmo endereço fixo da Cloudflare para todos.
        for (int attempt = 1; attempt <= 20; attempt++) {
            login(fromWorker("203.0.113.70"), "worker" + attempt + "@test.dev");
        }
        login(fromWorker("203.0.113.70"), "worker21@test.dev").andExpect(status().isTooManyRequests());

        login(fromWorker("203.0.113.71"), "worker22@test.dev").andExpect(status().isUnauthorized());
    }

    @Test
    void registrationIsLimitedPerIp() throws Exception {
        for (int attempt = 1; attempt <= 5; attempt++) {
            register(fromCloudflare("203.0.113.80", post("/auth/register")), "cadastro" + attempt + "@test.dev")
                    .andExpect(status().isCreated());
        }

        register(fromCloudflare("203.0.113.80", post("/auth/register")), "cadastro6@test.dev")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "720"))
                .andExpect(jsonPath("$.detail").value("Muitas tentativas em pouco tempo. Tente de novo em 12 minutos."));
    }

    @Test
    void forgotPasswordIsLimitedPerIpWhetherOrNotTheAccountExists() throws Exception {
        for (int attempt = 1; attempt <= 5; attempt++) {
            forgotPassword(fromCloudflare("203.0.113.90", post("/auth/forgot-password")), "talvez" + attempt + "@test.dev")
                    .andExpect(status().isAccepted());
        }

        forgotPassword(fromCloudflare("203.0.113.90", post("/auth/forgot-password")), "talvez6@test.dev")
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void refreshIsLimitedPerIp() throws Exception {
        for (int attempt = 1; attempt <= 30; attempt++) {
            mockMvc.perform(fromCloudflare("203.0.113.100", post("/auth/refresh")).header("X-Refresh-Request", "1"))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(fromCloudflare("203.0.113.100", post("/auth/refresh")).header("X-Refresh-Request", "1"))
                .andExpect(status().isTooManyRequests());
    }

    private ResultActions login(MockHttpServletRequestBuilder request, String email) throws Exception {
        return mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON).content("""
                {"email": "%s", "password": "senhaErrada1"}
                """.formatted(email)));
    }

    private ResultActions register(MockHttpServletRequestBuilder request, String email) throws Exception {
        return mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON).content("""
                {"name": "Teste", "email": "%s", "password": "senha12345"}
                """.formatted(email)));
    }

    private ResultActions forgotPassword(MockHttpServletRequestBuilder request, String email) throws Exception {
        return mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON).content("""
                {"email": "%s"}
                """.formatted(email)));
    }

    /** Chamada direta ao Render: a Cloudflare da frente dele escreve o CF-Connecting-IP. */
    private static MockHttpServletRequestBuilder fromCloudflare(String ip) {
        return fromCloudflare(ip, post("/auth/login"));
    }

    private static MockHttpServletRequestBuilder fromCloudflare(String ip, MockHttpServletRequestBuilder request) {
        return request.header("CF-Connecting-IP", ip);
    }

    /** Chamada pelo Worker: IP real em X-Client-IP, com o segredo, e o endereço fixo da Cloudflare. */
    private static MockHttpServletRequestBuilder fromWorker(String ip) {
        return post("/auth/login")
                .header("CF-Connecting-IP", "2a06:98c0:3600::103")
                .header("X-Client-IP", ip)
                .header("X-Proxy-Auth", PROXY_SECRET);
    }
}
