package com.akiratochiro.life_and_money_api.bill;

import com.akiratochiro.life_and_money_api.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** As ocorrências calculadas e o status de cada uma, contra o relógio de São Paulo. */
class BillOccurrencesIntegrationTest extends IntegrationTest {

    /** 15/11/2026, meio-dia em São Paulo. */
    private static final Instant NOV_15_NOON = Instant.parse("2026-11-15T15:00:00Z");

    @Test
    void theMonthListsEveryOccurrenceWithItsStatus() throws Exception {
        // given
        clock.setTo(NOV_15_NOON);
        Long user = register("ocorrencias.status@test.dev");
        Long home = createCategory(user, "Casa", "EXPENSE");
        Long freelance = createCategory(user, "Freela", "INCOME");
        createBill(user, home, "Aluguel", "1500.00", 10, "MONTHLY", "2026-01");
        createBill(user, home, "Condomínio", "600.00", 15, "MONTHLY", "2026-01");
        createBill(user, freelance, "Cliente X", "2000.00", 20, "MONTHLY", "2026-01");

        // when / then
        occurrences(user, "2026-11")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].name").value("Aluguel"))
                .andExpect(jsonPath("$[0].dueDate").value("2026-11-10"))
                .andExpect(jsonPath("$[0].status").value("OVERDUE"))
                .andExpect(jsonPath("$[0].type").value("EXPENSE"))
                .andExpect(jsonPath("$[0].month").value("2026-11"))
                .andExpect(jsonPath("$[1].name").value("Condomínio"))
                .andExpect(jsonPath("$[1].status").value("PENDING"))
                .andExpect(jsonPath("$[2].name").value("Cliente X"))
                .andExpect(jsonPath("$[2].type").value("INCOME"))
                .andExpect(jsonPath("$[2].expectedAmount").value(2000.00))
                .andExpect(jsonPath("$[2].status").value("PENDING"));
    }

    @Test
    void aBillOnThe31stFallsOnTheLastDayOfFebruaryAndOf30DayMonths() throws Exception {
        clock.setTo(NOV_15_NOON);
        Long user = register("ocorrencias.dia31@test.dev");
        Long home = createCategory(user, "Casa", "EXPENSE");
        createBill(user, home, "Aluguel", "1500.00", 31, "MONTHLY", "2026-11");

        occurrences(user, "2027-02").andExpect(jsonPath("$[0].dueDate").value("2027-02-28"));
        occurrences(user, "2028-02").andExpect(jsonPath("$[0].dueDate").value("2028-02-29"));
        occurrences(user, "2027-04").andExpect(jsonPath("$[0].dueDate").value("2027-04-30"));
        occurrences(user, "2026-11").andExpect(jsonPath("$[0].dueDate").value("2026-11-30"));
        occurrences(user, "2026-12").andExpect(jsonPath("$[0].dueDate").value("2026-12-31"));
    }

    @Test
    void aPastMonthKeepsTheAmountOfItsTime() throws Exception {
        // given: a luz era R$ 180 e passou a R$ 210 em novembro
        clock.setTo(NOV_15_NOON);
        Long user = register("ocorrencias.vigencia@test.dev");
        Long power = createCategory(user, "Energia", "EXPENSE");
        Long bill = createBill(user, power, "Luz", "180.00", 10, "MONTHLY", "2026-08");
        mockMvc.perform(put("/bills/{id}", bill).with(asUser(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId": %d, "name": "Luz", "amount": 210.00, "dueDay": 10}
                                """.formatted(power)))
                .andExpect(status().isOk());

        // then
        occurrences(user, "2026-10").andExpect(jsonPath("$[0].expectedAmount").value(180.00));
        occurrences(user, "2026-11").andExpect(jsonPath("$[0].expectedAmount").value(210.00));
        occurrences(user, "2027-03").andExpect(jsonPath("$[0].expectedAmount").value(210.00));
        occurrences(user, "2026-07").andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void aBillDueTodayIsStillPendingLateAtNightInSaoPaulo() throws Exception {
        // given: 23:30 de 15/11 em São Paulo, que já é 16/11 em UTC
        clock.setTo(Instant.parse("2026-11-16T02:30:00Z"));
        Long user = register("ocorrencias.fuso@test.dev");
        Long home = createCategory(user, "Casa", "EXPENSE");
        createBill(user, home, "Condomínio", "600.00", 15, "MONTHLY", "2026-11");

        // then
        occurrences(user, "2026-11").andExpect(jsonPath("$[0].status").value("PENDING"));

        // e à meia-noite em São Paulo ela atrasa
        clock.setTo(Instant.parse("2026-11-16T03:00:00Z"));
        occurrences(user, "2026-11").andExpect(jsonPath("$[0].status").value("OVERDUE"));
    }

    @Test
    void aYearlyBillShowsUpOnlyInItsMonth() throws Exception {
        clock.setTo(NOV_15_NOON);
        Long user = register("ocorrencias.anual@test.dev");
        Long car = createCategory(user, "Carro", "EXPENSE");
        createBill(user, car, "IPVA", "1200.00", 20, "YEARLY", "2027-01");

        occurrences(user, "2027-01").andExpect(jsonPath("$[0].recurrence").value("YEARLY"));
        occurrences(user, "2027-02").andExpect(jsonPath("$.length()").value(0));
        occurrences(user, "2028-01").andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void upcomingBringsOverdueAndTheNextSevenDaysAcrossTheYear() throws Exception {
        // given: 28/12/2026
        clock.setTo(Instant.parse("2026-12-28T15:00:00Z"));
        Long user = register("ocorrencias.proximas@test.dev");
        Long home = createCategory(user, "Casa", "EXPENSE");
        createBill(user, home, "Aluguel", "1500.00", 10, "MONTHLY", "2026-12");   // 10/12 atrasada; 10/01 longe
        createBill(user, home, "Cartão", "900.00", 30, "MONTHLY", "2026-12");     // 30/12
        createBill(user, home, "Escola", "700.00", 2, "MONTHLY", "2027-01");      // 02/01
        createBill(user, home, "Seguro", "300.00", 5, "MONTHLY", "2027-01");      // 05/01, fora dos 7 dias

        // when / then
        mockMvc.perform(get("/bills/upcoming").with(asUser(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].dueDate").value("2026-12-10"))
                .andExpect(jsonPath("$[0].status").value("OVERDUE"))
                .andExpect(jsonPath("$[1].dueDate").value("2026-12-30"))
                .andExpect(jsonPath("$[2].dueDate").value("2027-01-02"));
    }

    @Test
    void anotherUsersBillsDoNotShowUp() throws Exception {
        clock.setTo(NOV_15_NOON);
        Long ana = register("ocorrencias.ana@test.dev");
        Long bia = register("ocorrencias.bia@test.dev");
        Long home = createCategory(ana, "Casa", "EXPENSE");
        createBill(ana, home, "Aluguel", "1500.00", 10, "MONTHLY", "2026-01");

        occurrences(bia, "2026-11").andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/bills/upcoming").with(asUser(bia)))
                .andExpect(jsonPath("$.length()").value(0));
    }

    private ResultActions occurrences(Long userId, String month) throws Exception {
        return mockMvc.perform(get("/bills/occurrences").param("month", month).with(asUser(userId)));
    }

    private Long createBill(Long userId, Long categoryId, String name, String amount, int dueDay,
                            String recurrence, String startMonth) throws Exception {
        return idOf(mockMvc.perform(post("/bills").with(asUser(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BillIntegrationTest.billBody(categoryId, name, amount, dueDay,
                                recurrence, startMonth, null)))
                .andExpect(status().isCreated())
                .andReturn());
    }
}
