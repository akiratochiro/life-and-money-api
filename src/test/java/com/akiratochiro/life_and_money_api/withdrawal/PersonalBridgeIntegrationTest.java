package com.akiratochiro.life_and_money_api.withdrawal;

import com.akiratochiro.life_and_money_api.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A ponte PJ → PF (Fase 6): o pró-labore de 3.000 (10/2026, pago em 30/10) tem líquido de 2.670,00
 * (INSS 330, IRRF zero), e é esse valor que entra como receita. Hoje: 11/11/2026.
 */
class PersonalBridgeIntegrationTest extends IntegrationTest {

    @BeforeEach
    void fixToday() {
        clock.setTo(Instant.parse("2026-11-11T15:00:00Z"));
    }

    @Test
    void launchingCreatesAnIncomeWithTheNetAndTheDefaultCategoryOnce() throws Exception {
        Long ana = register("ponte.lancar@test.dev");
        Long company = company(ana);
        Long me = me(ana, company);
        Long october = proLabore(ana, company, me, "2026-10", "2026-10-30", "3000");

        launch(ana, company, october, null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.personalTransactionId").isNumber());
        mockMvc.perform(get("/transactions").param("month", "2026-10").with(asUser(ana)))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].amount").value(2670.00))
                .andExpect(jsonPath("$[0].transactionDate").value("2026-10-30"))
                .andExpect(jsonPath("$[0].description").value("Pró-labore 10/2026 · Ana Design"))
                .andExpect(jsonPath("$[0].origin.kind").value("WITHDRAWAL"))
                .andExpect(jsonPath("$[0].origin.withdrawalId").value(october))
                .andExpect(jsonPath("$[0].origin.editable").value(true));
        launch(ana, company, october, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Esta retirada já está lançada nas suas finanças."));

        Long november = proLabore(ana, company, me, "2026-11", "2026-11-10", "3000");
        launch(ana, company, november, null).andExpect(status().isCreated());
        Long profit = idOf(mockMvc.perform(post("/companies/{id}/withdrawals", company).with(asUser(ana))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ProfitDistributionIntegrationTest.distribution(me, "2026-11-10", "1000", null)))
                .andReturn());
        launch(ana, company, profit, null).andExpect(status().isCreated());

        mockMvc.perform(get("/categories").with(asUser(ana)))
                .andExpect(jsonPath("$[?(@.name == 'Pró-labore')].type", contains("INCOME")))
                .andExpect(jsonPath("$[?(@.name == 'Lucros distribuídos')].type", contains("INCOME")));
    }

    @Test
    void launchRules() throws Exception {
        Long ana = register("ponte.regras@test.dev");
        Long company = company(ana);
        Long partner = idOf(mockMvc.perform(post("/companies/{id}/partners", company).with(asUser(ana))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"Bruno\"}")).andReturn());
        Long brunos = proLabore(ana, company, partner, "2026-10", "2026-10-30", "3000");
        Long mine = proLabore(ana, company, me(ana, company), "2026-10", "2026-10-30", "3000");
        Long expense = createCategory(ana, "Padaria da esquina", "EXPENSE");

        launch(ana, company, brunos, null)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("Só a retirada do sócio que é você entra nas suas finanças."));
        launch(ana, company, mine, expense)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("Escolha uma categoria de receita."));
        Long salary = createCategory(ana, "Bicos de design", "INCOME");
        launch(ana, company, mine, salary).andExpect(status().isCreated());
        mockMvc.perform(get("/transactions").param("month", "2026-10").with(asUser(ana)))
                .andExpect(jsonPath("$[0].categoryId").value(salary));
    }

    /** Editar a retirada para 7.000 muda o lançamento para o líquido novo, 5.472,09; pelo extrato, valor e data travam. */
    @Test
    void theWithdrawalIsTheSourceOfTheAmountAndTheDate() throws Exception {
        Long ana = register("ponte.editar@test.dev");
        Long company = company(ana);
        Long me = me(ana, company);
        Long id = proLabore(ana, company, me, "2026-10", "2026-10-30", "3000");
        Long transaction = transactionOf(launch(ana, company, id, null));

        mockMvc.perform(put("/companies/{id}/withdrawals/{wid}", company, id).with(asUser(ana))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(WithdrawalIntegrationTest.body(me, "2026-10", "2026-10-31", "7000")))
                .andExpect(status().isOk());
        String listed = mockMvc.perform(get("/transactions").param("month", "2026-10").with(asUser(ana)))
                .andExpect(jsonPath("$[0].amount").value(5472.09))
                .andExpect(jsonPath("$[0].transactionDate").value("2026-10-31"))
                .andReturn().getResponse().getContentAsString();
        Long category = JsonPath.parse(listed).read("$[0].categoryId", Long.class);

        updateTransaction(ana, transaction, category, "5000.00", "2026-10-31", "x")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("Este lançamento vem de Pró-labore 10/2026 · Ana Design: mude o valor e "
                        + "a data na retirada, na página da empresa."));
        updateTransaction(ana, transaction, category, "5472.09", "2026-10-31", "Meu pró-labore")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Meu pró-labore"))
                .andExpect(jsonPath("$.origin.kind").value("WITHDRAWAL"));
    }

    @Test
    void deletingFromEitherSideAsksAboutTheOther() throws Exception {
        Long ana = register("ponte.excluir@test.dev");
        Long company = idOf(mockMvc.perform(post("/companies").with(asUser(ana)).contentType(MediaType.APPLICATION_JSON)
                        .content(ProfitDistributionIntegrationTest.SIMPLES.replace("2026-10-01", "2026-01-01")))
                .andExpect(status().isCreated()).andReturn());
        Long me = me(ana, company);

        // Pela empresa, sem levar o lançamento: ele fica, sem a origem.
        Long first = proLabore(ana, company, me, "2026-08", "2026-08-29", "3000");
        Long kept = transactionOf(launch(ana, company, first, null));
        mockMvc.perform(delete("/companies/{id}/withdrawals/{wid}", company, first).with(asUser(ana)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/transactions").param("month", "2026-08").with(asUser(ana)))
                .andExpect(jsonPath("$[0].id").value(kept))
                .andExpect(jsonPath("$[0].origin").value(nullValue()));

        // Pela empresa, levando o lançamento junto.
        Long second = proLabore(ana, company, me, "2026-09", "2026-09-30", "3000");
        launch(ana, company, second, null);
        mockMvc.perform(delete("/companies/{id}/withdrawals/{wid}", company, second).param("deletePersonal", "true").with(asUser(ana)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/transactions").param("month", "2026-09").with(asUser(ana))).andExpect(jsonPath("$").isEmpty());

        // Pelo extrato: a retirada fica, sem a ligação.
        Long third = proLabore(ana, company, me, "2026-10", "2026-10-30", "3000");
        Long transaction = transactionOf(launch(ana, company, third, null));
        mockMvc.perform(delete("/transactions/{id}", transaction).with(asUser(ana))).andExpect(status().isNoContent());
        mockMvc.perform(get("/companies/{id}/withdrawals", company).param("year", "2026").with(asUser(ana)))
                .andExpect(jsonPath("$[?(@.id == " + third + ")].personalTransactionId", contains(nullValue())));
    }

    @Test
    void anotherUsersWithdrawalCannotBeLaunched() throws Exception {
        Long ana = register("ponte.isolamento.ana@test.dev");
        Long bruno = register("ponte.isolamento.bruno@test.dev");
        Long anas = company(ana);
        Long withdrawal = proLabore(ana, anas, me(ana, anas), "2026-10", "2026-10-30", "3000");

        launch(bruno, anas, withdrawal, null).andExpect(status().isNotFound());
        Long brunos = company(bruno);
        launch(bruno, brunos, withdrawal, null).andExpect(status().isNotFound());
    }

    // Apoio

    Long company(Long userId) throws Exception {
        return idOf(mockMvc.perform(post("/companies").with(asUser(userId)).contentType(MediaType.APPLICATION_JSON)
                        .content(ProfitDistributionIntegrationTest.SIMPLES))
                .andExpect(status().isCreated()).andReturn());
    }

    Long me(Long userId, Long companyId) throws Exception {
        String body = mockMvc.perform(get("/companies/{id}/partners", companyId).with(asUser(userId)))
                .andReturn().getResponse().getContentAsString();
        List<Integer> ids = JsonPath.parse(body).read("$[?(@.me == true)].id");
        return ids.getFirst().longValue();
    }

    Long proLabore(Long userId, Long companyId, Long partnerId, String competence, String date, String gross) throws Exception {
        return idOf(mockMvc.perform(post("/companies/{id}/withdrawals", companyId).with(asUser(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(WithdrawalIntegrationTest.body(partnerId, competence, date, gross)))
                .andExpect(status().isCreated()).andReturn());
    }

    ResultActions launch(Long userId, Long companyId, Long withdrawalId, Long categoryId) throws Exception {
        var request = post("/companies/{id}/withdrawals/{wid}/personal-transaction", companyId, withdrawalId).with(asUser(userId));
        if (categoryId != null) {
            request = request.contentType(MediaType.APPLICATION_JSON).content("{\"categoryId\": " + categoryId + "}");
        }
        return mockMvc.perform(request);
    }

    Long transactionOf(ResultActions launched) throws Exception {
        return JsonPath.parse(launched.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .read("$.personalTransactionId", Long.class);
    }

    ResultActions updateTransaction(Long userId, Long id, Long categoryId, String amount, String date, String description) throws Exception {
        return mockMvc.perform(put("/transactions/{id}", id).with(asUser(userId)).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"categoryId": %d, "amount": %s, "transactionDate": "%s", "description": "%s"}
                        """.formatted(categoryId, amount, date, description)));
    }
}
