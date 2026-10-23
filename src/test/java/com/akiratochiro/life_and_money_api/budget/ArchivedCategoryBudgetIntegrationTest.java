package com.akiratochiro.life_and_money_api.budget;

import com.akiratochiro.life_and_money_api.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.YearMonth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifica a ligação entre as duas features: arquivar uma categoria no pacote {@code category}
 * precisa encerrar o orçamento dela no pacote {@code budget}, pelo evento.
 */
class ArchivedCategoryBudgetIntegrationTest extends IntegrationTest {

    @Test
    void archivingACategoryEndsItsBudget() throws Exception {
        // given: uma categoria com limite definido neste mês
        Long user = register("arquiva.orcamento@test.dev");
        Long gym = createCategory(user, "Academia", "EXPENSE");
        setBudget(user, gym);

        mockMvc.perform(get("/budgets").param("month", thisMonth()).with(asUser(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        // when
        mockMvc.perform(post("/categories/{id}/archive", gym).with(asUser(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(true));

        // then: a linha sai do orçamento e do dashboard, sem precisar de outra chamada
        mockMvc.perform(get("/budgets").param("month", thisMonth()).with(asUser(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get("/dashboard/budget").param("month", thisMonth()).with(asUser(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void archivingACategoryWithoutBudgetStillWorks() throws Exception {
        // given
        Long user = register("arquiva.sem.orcamento@test.dev");
        Long leisure = createCategory(user, "Cinema", "EXPENSE");

        // when / then: não há orçamento a encerrar, e isso não é erro
        mockMvc.perform(post("/categories/{id}/archive", leisure).with(asUser(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(true));
    }

    @Test
    void archivingTwiceDoesNotFail() throws Exception {
        // given
        Long user = register("arquiva.duas.vezes@test.dev");
        Long gym = createCategory(user, "Academia", "EXPENSE");
        setBudget(user, gym);

        // when / then
        mockMvc.perform(post("/categories/{id}/archive", gym).with(asUser(user)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/categories/{id}/archive", gym).with(asUser(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(true));
    }

    private void setBudget(Long userId, Long categoryId) throws Exception {
        String body = """
                {"mode": "AMOUNT", "limitValue": 300.00}
                """;

        mockMvc.perform(put("/budgets/{categoryId}", categoryId)
                        .with(asUser(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    private static String thisMonth() {
        return YearMonth.now().toString();
    }
}
