package com.akiratochiro.life_and_money_api.bill;

import com.akiratochiro.life_and_money_api.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Arquivar uma categoria, no pacote category, encerra as contas dela no pacote bill, pelo evento. */
class ArchivedCategoryBillsIntegrationTest extends IntegrationTest {

    @BeforeEach
    void stopTheClock() {
        clock.setTo(Instant.parse("2026-11-15T15:00:00Z"));
    }

    @Test
    void archivingEndsTheBillAndKeepsItsPastMonths() throws Exception {
        // given: uma conta desde agosto, nunca paga, e outra de outra categoria
        Long user = register("arquiva.contas@test.dev");
        Long gym = createCategory(user, "Academia", "EXPENSE");
        Long home = createCategory(user, "Casa", "EXPENSE");
        createBill(user, gym, "Academia", "2026-08");
        createBill(user, home, "Aluguel", "2026-08");

        // when
        mockMvc.perform(post("/categories/{id}/archive", gym).with(asUser(user)))
                .andExpect(status().isOk());

        // then: outubro fica, novembro some; a outra categoria não muda
        mockMvc.perform(get("/bills/occurrences").param("month", "2026-10").with(asUser(user)))
                .andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(get("/bills/occurrences").param("month", "2026-11").with(asUser(user)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Aluguel"));
    }

    @Test
    void archivingKeepsAMonthPaidAhead() throws Exception {
        Long user = register("arquiva.contas.paga@test.dev");
        Long gym = createCategory(user, "Academia", "EXPENSE");
        Long bill = createBill(user, gym, "Academia", "2026-08");
        mockMvc.perform(post("/bills/{id}/occurrences/{month}/payment", bill, "2026-11").with(asUser(user)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/categories/{id}/archive", gym).with(asUser(user)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/bills/occurrences").param("month", "2026-11").with(asUser(user)))
                .andExpect(jsonPath("$[0].status").value("PAID"));
        mockMvc.perform(get("/bills/occurrences").param("month", "2026-12").with(asUser(user)))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void aBillThatHasNotStartedIsDeleted() throws Exception {
        Long user = register("arquiva.contas.futura@test.dev");
        Long gym = createCategory(user, "Academia", "EXPENSE");
        createBill(user, gym, "Academia", "2026-11");

        mockMvc.perform(post("/categories/{id}/archive", gym).with(asUser(user)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/bills").with(asUser(user)))
                .andExpect(jsonPath("$.length()").value(0));
    }

    private Long createBill(Long userId, Long categoryId, String name, String startMonth) throws Exception {
        return idOf(mockMvc.perform(post("/bills").with(asUser(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BillIntegrationTest.billBody(categoryId, name, "100.00", 10,
                                "MONTHLY", startMonth, null)))
                .andExpect(status().isCreated())
                .andReturn());
    }
}
