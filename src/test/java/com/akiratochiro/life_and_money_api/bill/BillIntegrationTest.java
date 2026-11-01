package com.akiratochiro.life_and_money_api.bill;

import com.akiratochiro.life_and_money_api.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Cadastro das contas e a vigência dos termos. O relógio fica parado em 15/11/2026. */
class BillIntegrationTest extends IntegrationTest {

    @BeforeEach
    void stopTheClock() {
        clock.setTo(Instant.parse("2026-11-15T15:00:00Z"));
    }

    @Test
    void aCreatedBillShowsUpInTheList() throws Exception {
        // given
        Long user = register("contas.cria@test.dev");
        Long rent = createCategory(user, "Aluguel", "EXPENSE");

        // when
        createBill(user, rent, "Aluguel", "1500.00", 5, "MONTHLY", "2026-11", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Aluguel"))
                .andExpect(jsonPath("$.amount").value(1500.00))
                .andExpect(jsonPath("$.dueDay").value(5))
                .andExpect(jsonPath("$.recurrence").value("MONTHLY"))
                .andExpect(jsonPath("$.startMonth").value("2026-11"))
                .andExpect(jsonPath("$.endMonth").doesNotExist());

        // then
        mockMvc.perform(get("/bills").with(asUser(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].categoryId").value(rent));
    }

    @Test
    void aOneOffBillEndsInItsOwnMonth() throws Exception {
        Long user = register("contas.unica@test.dev");
        Long repair = createCategory(user, "Conserto", "EXPENSE");

        createBill(user, repair, "Mecânico", "800.00", 20, "ONCE", "2026-12", "2027-06")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.endMonth").value("2026-12"));
    }

    @Test
    void aSavingCategoryIsRejected() throws Exception {
        Long user = register("contas.poupanca@test.dev");
        Long reserve = createCategory(user, "Reserva", "SAVING");

        createBill(user, reserve, "Aporte", "300.00", 10, "MONTHLY", "2026-11", null)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail")
                        .value("Contas a pagar e a receber usam categorias de receita ou de despesa."));
    }

    @Test
    void anIncomeCategoryIsAccepted() throws Exception {
        Long user = register("contas.receita@test.dev");
        Long salary = createCategory(user, "Freela", "INCOME");

        createBill(user, salary, "Cliente X", "2000.00", 10, "MONTHLY", "2026-11", null)
                .andExpect(status().isCreated());
    }

    @Test
    void anEndBeforeTheStartIsRejected() throws Exception {
        Long user = register("contas.periodo@test.dev");
        Long rent = createCategory(user, "Aluguel", "EXPENSE");

        createBill(user, rent, "Aluguel", "1500.00", 5, "MONTHLY", "2026-11", "2026-10")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.periodValid").value("O último mês não pode ser antes do primeiro."));
    }

    @Test
    void aDueDayOutsideTheMonthIsRejected() throws Exception {
        Long user = register("contas.dia@test.dev");
        Long rent = createCategory(user, "Aluguel", "EXPENSE");

        createBill(user, rent, "Aluguel", "1500.00", 32, "MONTHLY", "2026-11", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.dueDay").value("O dia de vencimento vai de 1 a 31."));
    }

    @Test
    void anotherUsersBillIsNotFound() throws Exception {
        // given
        Long ana = register("contas.ana@test.dev");
        Long bia = register("contas.bia@test.dev");
        Long rent = createCategory(ana, "Aluguel", "EXPENSE");
        Long bill = idOf(createBill(ana, rent, "Aluguel", "1500.00", 5, "MONTHLY", "2026-11", null).andReturn());

        // when / then: 404, e não 403, para não revelar que a conta existe
        updateBill(bia, bill, rent, "Aluguel", "1.00", 5, null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Conta não encontrada."));
        mockMvc.perform(delete("/bills/{id}", bill).with(asUser(bia)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/bills").with(asUser(bia)))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void changingABillWithHistoryKeepsThePastTerms() throws Exception {
        // given: a conta começou em agosto, e agora é novembro
        Long user = register("contas.vigencia@test.dev");
        Long power = createCategory(user, "Energia", "EXPENSE");
        Long bill = idOf(createBill(user, power, "Luz", "180.00", 10, "MONTHLY", "2026-08", null).andReturn());

        // when
        updateBill(user, bill, power, "Luz", "210.00", 12, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(210.00))
                .andExpect(jsonPath("$.dueDay").value(12));

        // then: agosto a outubro guardam o valor antigo, novembro em diante o novo
        mockMvc.perform(get("/users/me/export").with(asUser(user)))
                .andExpect(jsonPath("$.bills[0].terms.length()").value(2))
                .andExpect(jsonPath("$.bills[0].terms[0].amount").value(180.00))
                .andExpect(jsonPath("$.bills[0].terms[0].validFrom").value("2026-08"))
                .andExpect(jsonPath("$.bills[0].terms[0].validTo").value("2026-10"))
                .andExpect(jsonPath("$.bills[0].terms[1].amount").value(210.00))
                .andExpect(jsonPath("$.bills[0].terms[1].validFrom").value("2026-11"))
                .andExpect(jsonPath("$.bills[0].terms[1].validTo").doesNotExist());
    }

    @Test
    void changingABillTwiceInTheSameMonthKeepsOneVersionForTheMonth() throws Exception {
        Long user = register("contas.duas.vezes@test.dev");
        Long power = createCategory(user, "Energia", "EXPENSE");
        Long bill = idOf(createBill(user, power, "Luz", "180.00", 10, "MONTHLY", "2026-08", null).andReturn());

        updateBill(user, bill, power, "Luz", "210.00", 10, null).andExpect(status().isOk());
        updateBill(user, bill, power, "Luz", "230.00", 10, null).andExpect(status().isOk());

        mockMvc.perform(get("/users/me/export").with(asUser(user)))
                .andExpect(jsonPath("$.bills[0].terms.length()").value(2))
                .andExpect(jsonPath("$.bills[0].terms[1].amount").value(230.00));
    }

    @Test
    void changingABillThatStartsThisMonthKeepsNoHistory() throws Exception {
        Long user = register("contas.sem.historico@test.dev");
        Long power = createCategory(user, "Energia", "EXPENSE");
        Long bill = idOf(createBill(user, power, "Luz", "180.00", 10, "MONTHLY", "2026-11", null).andReturn());

        updateBill(user, bill, power, "Luz", "210.00", 10, null).andExpect(status().isOk());

        mockMvc.perform(get("/users/me/export").with(asUser(user)))
                .andExpect(jsonPath("$.bills[0].terms.length()").value(1))
                .andExpect(jsonPath("$.bills[0].terms[0].amount").value(210.00));
    }

    @Test
    void anUnchangedUpdateCreatesNoNewVersion() throws Exception {
        Long user = register("contas.igual@test.dev");
        Long power = createCategory(user, "Energia", "EXPENSE");
        Long bill = idOf(createBill(user, power, "Luz", "180.00", 10, "MONTHLY", "2026-08", null).andReturn());

        updateBill(user, bill, power, " Luz ", "180", 10, null).andExpect(status().isOk());

        mockMvc.perform(get("/users/me/export").with(asUser(user)))
                .andExpect(jsonPath("$.bills[0].terms.length()").value(1));
    }

    @Test
    void anEndedBillCannotChangeItsTermsUnlessItIsReopened() throws Exception {
        // given: terminou em setembro
        Long user = register("contas.encerrada@test.dev");
        Long gym = createCategory(user, "Academia", "EXPENSE");
        Long bill = idOf(createBill(user, gym, "Academia", "120.00", 5, "MONTHLY", "2026-03", "2026-09").andReturn());

        // when / then
        updateBill(user, bill, gym, "Academia", "130.00", 5, "2026-09")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(
                        "Esta conta já foi encerrada. Para mudar os valores, reabra a conta tirando o último mês."));

        updateBill(user, bill, gym, "Academia", "130.00", 5, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.endMonth").doesNotExist())
                .andExpect(jsonPath("$.amount").value(130.00));
    }

    @Test
    void anEndBeforeTheStartIsRejectedOnUpdate() throws Exception {
        Long user = register("contas.fim.invalido@test.dev");
        Long gym = createCategory(user, "Academia", "EXPENSE");
        Long bill = idOf(createBill(user, gym, "Academia", "120.00", 5, "MONTHLY", "2026-03", null).andReturn());

        updateBill(user, bill, gym, "Academia", "120.00", 5, "2026-02")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("O último mês não pode ser antes do primeiro."));
    }

    @Test
    void aBillWithoutPaymentsIsDeleted() throws Exception {
        Long user = register("contas.apaga@test.dev");
        Long gym = createCategory(user, "Academia", "EXPENSE");
        Long bill = idOf(createBill(user, gym, "Academia", "120.00", 5, "MONTHLY", "2026-03", null).andReturn());

        mockMvc.perform(delete("/bills/{id}", bill).with(asUser(user)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/bills").with(asUser(user)))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void anArchivedCategoryCannotTakeANewBill() throws Exception {
        Long user = register("contas.arquivada@test.dev");
        Long old = createCategory(user, "Antiga", "EXPENSE");
        mockMvc.perform(post("/categories/{id}/archive", old).with(asUser(user))).andExpect(status().isOk());

        createBill(user, old, "Antiga", "10.00", 5, "MONTHLY", "2026-11", null)
                .andExpect(status().isConflict());
    }

    static String billBody(Long categoryId, String name, String amount, int dueDay,
                           String recurrence, String startMonth, String endMonth) {
        return """
                {"categoryId": %d, "name": "%s", "amount": %s, "dueDay": %d,
                 "recurrence": "%s", "startMonth": "%s", "endMonth": %s}
                """.formatted(categoryId, name, amount, dueDay, recurrence, startMonth,
                endMonth == null ? "null" : "\"" + endMonth + "\"");
    }

    private ResultActions createBill(Long userId, Long categoryId, String name, String amount, int dueDay,
                                     String recurrence, String startMonth, String endMonth) throws Exception {
        return mockMvc.perform(post("/bills").with(asUser(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(billBody(categoryId, name, amount, dueDay, recurrence, startMonth, endMonth)));
    }

    private ResultActions updateBill(Long userId, Long billId, Long categoryId, String name, String amount,
                                     int dueDay, String endMonth) throws Exception {
        return mockMvc.perform(put("/bills/{id}", billId).with(asUser(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"categoryId": %d, "name": "%s", "amount": %s, "dueDay": %d, "endMonth": %s}
                        """.formatted(categoryId, name, amount, dueDay,
                        endMonth == null ? "null" : "\"" + endMonth + "\"")));
    }
}
