package com.akiratochiro.life_and_money_api.account;

import com.akiratochiro.life_and_money_api.IntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AccountIntegrationTest extends IntegrationTest {

    private static final String PASSWORD = "senha12345";

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void deletingAnAccountRemovesEveryRowOfItAndNothingOfAnyoneElse() throws Exception {
        Long ana = registerWithData("exclusao.ana@test.dev");
        Long bia = registerWithData("exclusao.bia@test.dev");
        Map<String, Long> biaBefore = rowsOf(bia);
        assertThat(rowsOf(ana).values()).as("A tem dados em todas as tabelas").allMatch(count -> count > 0);

        deleteAccount(ana, PASSWORD).andExpect(status().isNoContent());

        assertThat(rowsOf(ana).values()).as("nada de A sobra em nenhuma tabela").allMatch(count -> count == 0);
        assertThat(rowsOf(bia)).as("B fica exatamente como estava").isEqualTo(biaBefore);
        login("exclusao.bia@test.dev").andExpect(status().isOk());
    }

    @Test
    void aWrongPasswordDeletesNothingAndDoesNotEndTheSession() throws Exception {
        Long ana = registerWithData("exclusao.senha@test.dev");
        Map<String, Long> before = rowsOf(ana);

        // 403, e não 401: para o front, 401 é sessão encerrada.
        deleteAccount(ana, "senhaErrada1")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Senha incorreta."))
                .andExpect(jsonPath("$.errors.password").value("Senha incorreta."));

        assertThat(rowsOf(ana)).isEqualTo(before);
    }

    @Test
    void afterTheDeletionTheOldSessionNoLongerWorks() throws Exception {
        Long ana = registerWithData("exclusao.sessao@test.dev");
        String session = refreshCookieOf(login("exclusao.sessao@test.dev").andReturn());

        deleteAccount(ana, PASSWORD)
                .andExpect(header().string(HttpHeaders.SET_COOKIE,
                        org.hamcrest.Matchers.containsString("Max-Age=0")));

        mockMvc.perform(post("/auth/refresh")
                        .cookie(new Cookie("refreshToken", session))
                        .header("X-Refresh-Request", "1"))
                .andExpect(status().isUnauthorized());
        login("exclusao.sessao@test.dev").andExpect(status().isUnauthorized());
    }

    @Test
    void thePasswordIsRequired() throws Exception {
        Long ana = register("exclusao.vazia@test.dev");

        deleteAccount(ana, "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").value("Informe sua senha."));
    }

    @Test
    void theExportBringsEverythingOfTheUserAndNothingOfAnyoneElse() throws Exception {
        Long ana = registerWithData("export.ana@test.dev");
        Long bia = registerWithData("export.bia@test.dev");

        MvcResult result = mockMvc.perform(get("/users/me/export").with(asUser(ana)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.matchesPattern("attachment; filename=\"life-and-money-\\d{4}-\\d{2}-\\d{2}\\.json\"")))
                .andExpect(jsonPath("$.profile.email").value("export.ana@test.dev"))
                .andExpect(jsonPath("$.transactions.length()").value(2))
                .andExpect(jsonPath("$.budgetItems.length()").value(1))
                .andExpect(jsonPath("$.goals.length()").value(1))
                .andExpect(jsonPath("$.goals[0].saved").value(300.00))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body)
                .contains("Mercado " + ana, "Reserva " + ana)
                .doesNotContain("export.bia@test.dev", "Mercado " + bia, "Reserva " + bia)
                .as("credenciais não são dados da pessoa")
                .doesNotContainIgnoringCase("passwordHash")
                .doesNotContainIgnoringCase("tokenHash");
    }

    /** Linhas do usuário em cada tabela com user_id, mais a dele em users. */
    private Map<String, Long> rowsOf(Long userId) {
        List<String> tables = jdbc.queryForList("""
                select table_name from information_schema.columns
                 where table_schema = 'public' and column_name = 'user_id'
                 order by table_name
                """, String.class);
        assertThat(tables).as("as tabelas com dados de usuário")
                .contains("categories", "transactions", "budget_items", "savings_goals",
                        "refresh_tokens", "password_reset_tokens");

        Map<String, Long> rows = new LinkedHashMap<>();
        rows.put("users", jdbc.queryForObject("select count(*) from users where id = ?", Long.class, userId));
        for (String table : tables) {
            rows.put(table, jdbc.queryForObject(
                    "select count(*) from " + table + " where user_id = ?", Long.class, userId));
        }
        return rows;
    }

    /** Um usuário com pelo menos uma linha em cada tabela que guarda dados de usuário. */
    private Long registerWithData(String email) throws Exception {
        Long userId = register(email);
        Long expense = createCategory(userId, "Mercado " + userId, "EXPENSE");
        Long saving = createCategory(userId, "Reserva " + userId, "SAVING");
        Long goal = idOf(mockMvc.perform(post("/goals").with(asUser(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Viagem", "targetAmount": 3000, "deadline": "%s"}
                                """.formatted(LocalDate.now().plusMonths(6))))
                .andExpect(status().isCreated())
                .andReturn());

        createTransaction(userId, expense, null, "120.50");
        createTransaction(userId, saving, goal, "300.00");
        mockMvc.perform(put("/budgets/" + expense).with(asUser(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mode": "AMOUNT", "limitValue": 800}
                                """))
                .andExpect(status().isOk());

        login(email).andExpect(status().isOk());
        mockMvc.perform(post("/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s"}
                                """.formatted(email)))
                .andExpect(status().isAccepted());
        return userId;
    }

    private void createTransaction(Long userId, Long categoryId, Long goalId, String amount) throws Exception {
        mockMvc.perform(post("/transactions").with(asUser(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId": %d, "goalId": %s, "amount": %s, "transactionDate": "%s"}
                                """.formatted(categoryId, goalId, amount, LocalDate.now())))
                .andExpect(status().isCreated());
    }

    private ResultActions deleteAccount(Long userId, String password) throws Exception {
        return mockMvc.perform(delete("/users/me").with(asUser(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"password": "%s"}
                        """.formatted(password)));
    }

    private ResultActions login(String email) throws Exception {
        return mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "%s"}
                        """.formatted(email, PASSWORD)));
    }

    private static String refreshCookieOf(MvcResult result) {
        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).isNotNull().startsWith("refreshToken=");
        String value = setCookie.substring("refreshToken=".length());
        return value.substring(0, value.indexOf(';'));
    }
}
