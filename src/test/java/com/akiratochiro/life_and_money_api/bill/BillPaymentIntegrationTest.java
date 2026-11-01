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

/** Pagar cria a transação real; desfazer, ou excluí-la pelo extrato, devolve a ocorrência. */
class BillPaymentIntegrationTest extends IntegrationTest {

    @BeforeEach
    void stopTheClock() {
        // 15/11/2026, meio-dia em São Paulo
        clock.setTo(Instant.parse("2026-11-15T15:00:00Z"));
    }

    @Test
    void payingWithoutBodyUsesTheExpectedAmountAndToday() throws Exception {
        // given
        Long user = register("pagar.padrao@test.dev");
        Long home = createCategory(user, "Casa", "EXPENSE");
        Long rent = createBill(user, home, "Aluguel", "1500.00", 10, "2026-01");

        // when
        pay(user, rent, "2026-11", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.paidAmount").value(1500.00))
                .andExpect(jsonPath("$.paidOn").value("2026-11-15"))
                .andExpect(jsonPath("$.transactionId").isNumber());

        // then: a transação está no extrato, na categoria e com o nome da conta
        mockMvc.perform(get("/transactions").param("month", "2026-11").with(asUser(user)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].categoryId").value(home))
                .andExpect(jsonPath("$[0].amount").value(1500.00))
                .andExpect(jsonPath("$[0].description").value("Aluguel"));
        occurrences(user, "2026-11")
                .andExpect(jsonPath("$[0].status").value("PAID"));
    }

    @Test
    void thePaidAmountCanDifferFromTheExpectedOne() throws Exception {
        // given: a luz estava prevista em R$ 180 e veio R$ 213,47
        Long user = register("pagar.valor@test.dev");
        Long power = createCategory(user, "Energia", "EXPENSE");
        Long bill = createBill(user, power, "Luz", "180.00", 10, "2026-01");

        // when
        pay(user, bill, "2026-11", """
                {"amount": 213.47, "paidOn": "2026-11-12"}
                """)
                .andExpect(status().isCreated());

        // then: a previsão continua a mesma, o pago é o real
        occurrences(user, "2026-11")
                .andExpect(jsonPath("$[0].expectedAmount").value(180.00))
                .andExpect(jsonPath("$[0].paidAmount").value(213.47))
                .andExpect(jsonPath("$[0].paidOn").value("2026-11-12"));
    }

    @Test
    void payingAnOverdueMonthMakesItPaid() throws Exception {
        Long user = register("pagar.atrasada@test.dev");
        Long home = createCategory(user, "Casa", "EXPENSE");
        Long rent = createBill(user, home, "Aluguel", "1500.00", 10, "2026-01");
        occurrences(user, "2026-09").andExpect(jsonPath("$[0].status").value("OVERDUE"));

        pay(user, rent, "2026-09", null).andExpect(status().isCreated());

        occurrences(user, "2026-09").andExpect(jsonPath("$[0].status").value("PAID"));
        occurrences(user, "2026-10").andExpect(jsonPath("$[0].status").value("OVERDUE"));
    }

    @Test
    void receivingAnIncomeCreatesAnIncomeTransaction() throws Exception {
        Long user = register("pagar.receita@test.dev");
        Long freelance = createCategory(user, "Freela", "INCOME");
        Long client = createBill(user, freelance, "Cliente X", "2000.00", 20, "2026-01");

        pay(user, client, "2026-11", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("INCOME"));

        mockMvc.perform(get("/dashboard/monthly-totals").param("months", "1").with(asUser(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.months[0].income").value(2000.00));
    }

    @Test
    void payingTheSameMonthTwiceIsRejected() throws Exception {
        Long user = register("pagar.duas.vezes@test.dev");
        Long home = createCategory(user, "Casa", "EXPENSE");
        Long rent = createBill(user, home, "Aluguel", "1500.00", 10, "2026-01");
        pay(user, rent, "2026-11", null).andExpect(status().isCreated());

        pay(user, rent, "2026-11", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Este mês já está marcado como pago."));

        mockMvc.perform(get("/transactions").param("month", "2026-11").with(asUser(user)))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void aMonthTheBillDoesNotOccurInCannotBePaid() throws Exception {
        Long user = register("pagar.fora@test.dev");
        Long car = createCategory(user, "Carro", "EXPENSE");
        Long ipva = idOf(mockMvc.perform(post("/bills").with(asUser(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BillIntegrationTest.billBody(car, "IPVA", "1200.00", 20, "YEARLY", "2026-03", null)))
                .andReturn());

        pay(user, ipva, "2026-11", null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Esta conta não vence nesse mês."));
        pay(user, ipva, "2025-03", null).andExpect(status().isNotFound()); // antes do início
    }

    @Test
    void anInvalidAmountIsRejected() throws Exception {
        Long user = register("pagar.invalido@test.dev");
        Long home = createCategory(user, "Casa", "EXPENSE");
        Long rent = createBill(user, home, "Aluguel", "1500.00", 10, "2026-01");

        pay(user, rent, "2026-11", """
                {"amount": -5}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.amount").value("O valor pago deve ser maior que zero."));
    }

    @Test
    void undoingRemovesTheTransactionAndTheMonthIsDueAgain() throws Exception {
        // given
        Long user = register("pagar.desfaz@test.dev");
        Long home = createCategory(user, "Casa", "EXPENSE");
        Long rent = createBill(user, home, "Aluguel", "1500.00", 20, "2026-01");
        pay(user, rent, "2026-11", null).andExpect(status().isCreated());

        // when
        mockMvc.perform(delete("/bills/{id}/occurrences/{month}/payment", rent, "2026-11").with(asUser(user)))
                .andExpect(status().isNoContent());

        // then
        occurrences(user, "2026-11")
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[0].paidAmount").doesNotExist());
        mockMvc.perform(get("/transactions").param("month", "2026-11").with(asUser(user)))
                .andExpect(jsonPath("$.length()").value(0));

        // e pode ser paga de novo
        pay(user, rent, "2026-11", null).andExpect(status().isCreated());
    }

    @Test
    void undoingAMonthThatIsNotPaidIsNotFound() throws Exception {
        Long user = register("pagar.desfaz.nada@test.dev");
        Long home = createCategory(user, "Casa", "EXPENSE");
        Long rent = createBill(user, home, "Aluguel", "1500.00", 20, "2026-01");

        mockMvc.perform(delete("/bills/{id}/occurrences/{month}/payment", rent, "2026-11").with(asUser(user)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Este mês não está marcado como pago."));
    }

    @Test
    void deletingThePaymentTransactionFromTheStatementUndoesThePayment() throws Exception {
        // given
        Long user = register("pagar.extrato@test.dev");
        Long home = createCategory(user, "Casa", "EXPENSE");
        Long rent = createBill(user, home, "Aluguel", "1500.00", 10, "2026-01");
        Long transaction = transactionIdOf(pay(user, rent, "2026-11", null));

        // when: excluída pelo extrato, não pela tela de contas
        mockMvc.perform(delete("/transactions/{id}", transaction).with(asUser(user)))
                .andExpect(status().isNoContent());

        // then
        occurrences(user, "2026-11").andExpect(jsonPath("$[0].status").value("OVERDUE"));
    }

    @Test
    void editingThePaymentTransactionChangesThePaidAmount() throws Exception {
        Long user = register("pagar.edita@test.dev");
        Long power = createCategory(user, "Energia", "EXPENSE");
        Long bill = createBill(user, power, "Luz", "180.00", 10, "2026-01");
        Long transaction = transactionIdOf(pay(user, bill, "2026-11", null));

        mockMvc.perform(put("/transactions/{id}", transaction).with(asUser(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId": %d, "amount": 199.90, "transactionDate": "2026-11-14"}
                                """.formatted(power)))
                .andExpect(status().isOk());

        occurrences(user, "2026-11")
                .andExpect(jsonPath("$[0].paidAmount").value(199.90))
                .andExpect(jsonPath("$[0].paidOn").value("2026-11-14"));
    }

    @Test
    void anotherUserCannotPayOrUndo() throws Exception {
        Long ana = register("pagar.ana@test.dev");
        Long bia = register("pagar.bia@test.dev");
        Long home = createCategory(ana, "Casa", "EXPENSE");
        Long rent = createBill(ana, home, "Aluguel", "1500.00", 10, "2026-01");
        pay(ana, rent, "2026-10", null).andExpect(status().isCreated());

        pay(bia, rent, "2026-11", null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Conta não encontrada."));
        mockMvc.perform(delete("/bills/{id}/occurrences/{month}/payment", rent, "2026-10").with(asUser(bia)))
                .andExpect(status().isNotFound());
        occurrences(ana, "2026-10").andExpect(jsonPath("$[0].status").value("PAID"));
    }

    @Test
    void removingABillWithPaymentsEndsItAndKeepsThePast() throws Exception {
        // given: paga em setembro, outubro em aberto
        Long user = register("pagar.remove@test.dev");
        Long gym = createCategory(user, "Academia", "EXPENSE");
        Long bill = createBill(user, gym, "Academia", "120.00", 5, "2026-08");
        pay(user, bill, "2026-09", null).andExpect(status().isCreated());

        // when
        mockMvc.perform(delete("/bills/{id}", bill).with(asUser(user)))
                .andExpect(status().isNoContent());

        // then: encerrada em outubro, o mês anterior ao atual
        mockMvc.perform(get("/bills").with(asUser(user)))
                .andExpect(jsonPath("$[0].endMonth").value("2026-10"));
        occurrences(user, "2026-09").andExpect(jsonPath("$[0].status").value("PAID"));
        occurrences(user, "2026-10").andExpect(jsonPath("$[0].status").value("OVERDUE"));
        occurrences(user, "2026-11").andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void removingABillPaidAheadKeepsThePaidMonth() throws Exception {
        Long user = register("pagar.adiantado@test.dev");
        Long gym = createCategory(user, "Academia", "EXPENSE");
        Long bill = createBill(user, gym, "Academia", "120.00", 5, "2026-08");
        pay(user, bill, "2027-01", null).andExpect(status().isCreated());

        mockMvc.perform(delete("/bills/{id}", bill).with(asUser(user)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/bills").with(asUser(user)))
                .andExpect(jsonPath("$[0].endMonth").value("2027-01"));
    }

    @Test
    void endingBeforeAPaidMonthIsRejected() throws Exception {
        Long user = register("pagar.fim@test.dev");
        Long gym = createCategory(user, "Academia", "EXPENSE");
        Long bill = createBill(user, gym, "Academia", "120.00", 5, "2026-08");
        pay(user, bill, "2026-12", null).andExpect(status().isCreated());

        mockMvc.perform(put("/bills/{id}", bill).with(asUser(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId": %d, "name": "Academia", "amount": 120.00, "dueDay": 5, "endMonth": "2026-11"}
                                """.formatted(gym)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(
                        "Há um mês pago depois desse fim. Desfaça esse pagamento antes de encerrar a conta."));
    }

    private ResultActions pay(Long userId, Long billId, String month, String body) throws Exception {
        var request = post("/bills/{id}/occurrences/{month}/payment", billId, month).with(asUser(userId));
        if (body != null) {
            request = request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request);
    }

    private ResultActions occurrences(Long userId, String month) throws Exception {
        return mockMvc.perform(get("/bills/occurrences").param("month", month).with(asUser(userId)));
    }

    private Long createBill(Long userId, Long categoryId, String name, String amount, int dueDay,
                            String startMonth) throws Exception {
        return idOf(mockMvc.perform(post("/bills").with(asUser(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BillIntegrationTest.billBody(categoryId, name, amount, dueDay,
                                "MONTHLY", startMonth, null)))
                .andExpect(status().isCreated())
                .andReturn());
    }

    private static Long transactionIdOf(ResultActions result) throws Exception {
        return com.jayway.jsonpath.JsonPath.parse(result.andExpect(status().isCreated()).andReturn()
                .getResponse().getContentAsString()).read("$.transactionId", Long.class);
    }
}
