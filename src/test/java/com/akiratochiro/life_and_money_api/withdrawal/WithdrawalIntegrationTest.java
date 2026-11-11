package com.akiratochiro.life_and_money_api.withdrawal;

import com.akiratochiro.life_and_money_api.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
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

class WithdrawalIntegrationTest extends IntegrationTest {

    static final String SIMPLES = """
            {"name": "Ana Design", "emoji": "🎨", "openingDate": "2024-03-10",
             "legalNature": "SLU", "taxRegime": "SIMPLES_NACIONAL", "annexConfig": "FATOR_R"}
            """;
    static final String MEI = """
            {"name": "Solo", "emoji": "💼", "openingDate": "2024-03-10",
             "legalNature": "MEI", "taxRegime": "SIMEI", "meiActivity": "SERVICOS"}
            """;

    @BeforeEach
    void fixToday() {
        clock.setTo(Instant.parse("2026-11-11T15:00:00Z"));
    }

    /** O exemplo do plano: pró-labore de 7.000 → INSS 770,00, IRRF 757,91, líquido 5.472,09. */
    @Test
    void aProLaboreHasItsInssAndIrrfCalculated() throws Exception {
        Long ana = register("prolabore.calculo@test.dev");
        Long company = company(ana, SIMPLES);

        proLabore(ana, company, me(ana, company), "2026-10", "2026-10-30", "7000")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind").value("PRO_LABORE"))
                .andExpect(jsonPath("$.competenceMonth").value("2026-10"))
                .andExpect(jsonPath("$.inssAmount").value(770.00))
                .andExpect(jsonPath("$.irrfAmount").value(757.91))
                .andExpect(jsonPath("$.netAmount").value(5472.09))
                .andExpect(jsonPath("$.calculated").value(true))
                .andExpect(jsonPath("$.warnings").isEmpty());
    }

    /** Antes de 2026 o app não calcula: os valores do holerite são aceitos, para a folha do Fator R ter o histórico. */
    @Test
    void beforeTheCalculatedPeriodTheWithheldValuesAreInformed() throws Exception {
        Long ana = register("prolabore.antigo@test.dev");
        Long company = company(ana, SIMPLES);
        Long me = me(ana, company);

        proLabore(ana, company, me, "2025-06", "2025-06-30", "5000")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("O app não calcula impostos de 06/2025. Informe o INSS e o IRRF retidos, "
                        + "como estão no holerite."));
        mockMvc.perform(post("/companies/{id}/withdrawals", company).with(asUser(ana))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"partnerId": %d, "kind": "PRO_LABORE", "competenceMonth": "2025-06", "paymentDate": "2025-06-30",
                                 "grossAmount": 5000, "inssAmount": 550, "irrfAmount": 400.50}
                                """.formatted(me)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.netAmount").value(4049.50))
                .andExpect(jsonPath("$.calculated").value(false));
    }

    @Test
    void aMeiHasNoProLabore() throws Exception {
        Long ana = register("prolabore.mei@test.dev");
        Long company = company(ana, MEI);

        proLabore(ana, company, me(ana, company), "2026-10", "2026-10-30", "3000")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("O MEI não tem pró-labore: a contribuição do titular já está no DAS-MEI. "
                        + "A retirada como lucro chega na próxima fase do app."));
    }

    @Test
    void onePerPartnerPerMonthAndNoFuturePayment() throws Exception {
        Long ana = register("prolabore.regras@test.dev");
        Long company = company(ana, SIMPLES);
        Long me = me(ana, company);
        proLabore(ana, company, me, "2026-10", "2026-10-30", "3000").andExpect(status().isCreated());

        proLabore(ana, company, me, "2026-10", "2026-10-31", "3000")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Já existe o pró-labore de 10/2026 para Teste."));
        proLabore(ana, company, me, "2026-11", "2026-11-12", "3000")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("A data do pagamento não pode estar no futuro: a retirada registra o que já saiu da conta."));
        proLabore(ana, company, me, "2026-11", "2026-10-30", "3000")
                .andExpect(jsonPath("$.detail").value("O pagamento não pode ser antes do mês da folha."));
        proLabore(ana, company, me, "2024-02", "2024-02-28", "3000")
                .andExpect(jsonPath("$.detail").value("O mês da folha é anterior à abertura da empresa."));
    }

    @Test
    void warningsForAPaymentInAnotherMonthAndBelowTheMinimumWage() throws Exception {
        Long ana = register("prolabore.avisos@test.dev");
        Long company = company(ana, SIMPLES);

        proLabore(ana, company, me(ana, company), "2026-09", "2026-10-05", "1000")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.warnings.length()").value(2));
    }

    @Test
    void editingRecalculatesAndDeletingRemoves() throws Exception {
        Long ana = register("prolabore.editar@test.dev");
        Long company = company(ana, SIMPLES);
        Long me = me(ana, company);
        Long id = idOf(proLabore(ana, company, me, "2026-10", "2026-10-30", "3000").andReturn());

        mockMvc.perform(put("/companies/{id}/withdrawals/{wid}", company, id).with(asUser(ana))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(me, "2026-10", "2026-10-31", "7000")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.irrfAmount").value(757.91));
        mockMvc.perform(get("/companies/{id}/withdrawals", company).param("year", "2026").with(asUser(ana)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].grossAmount").value(7000));
        mockMvc.perform(delete("/companies/{id}/withdrawals/{wid}", company, id).with(asUser(ana)))
                .andExpect(status().isNoContent());
    }

    @Test
    void thePlannedProLaboreHasEffectiveDating() throws Exception {
        Long ana = register("prolabore.plano@test.dev");
        Long company = company(ana, SIMPLES);
        Long me = me(ana, company);

        mockMvc.perform(put("/companies/{id}/partners/{pid}/pro-labore-plan", company, me).with(asUser(ana))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"grossAmount\": 7000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validFrom").value("2026-11"));
        // Mudar no mesmo mês troca no lugar; o planejado de novembro passa a ser 7.500.
        mockMvc.perform(put("/companies/{id}/partners/{pid}/pro-labore-plan", company, me).with(asUser(ana))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"grossAmount\": 7500}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/companies/{id}/pro-labore-plans", company).with(asUser(ana)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].grossAmount").value(7500));

        mockMvc.perform(delete("/companies/{id}/partners/{pid}/pro-labore-plan", company, me).with(asUser(ana)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/companies/{id}/pro-labore-plans", company).with(asUser(ana))).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void anotherUsersWithdrawalsAreNotFound() throws Exception {
        Long ana = register("prolabore.isolamento.ana@test.dev");
        Long bruno = register("prolabore.isolamento.bruno@test.dev");
        Long anas = company(ana, SIMPLES);
        Long brunos = company(bruno, SIMPLES);
        Long anasPartner = me(ana, anas);
        Long anasWithdrawal = idOf(proLabore(ana, anas, anasPartner, "2026-10", "2026-10-30", "3000").andReturn());

        mockMvc.perform(get("/companies/{id}/withdrawals", anas).param("year", "2026").with(asUser(bruno)))
                .andExpect(status().isNotFound());
        proLabore(bruno, brunos, anasPartner, "2026-10", "2026-10-30", "3000")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Sócio não encontrado."));
        mockMvc.perform(delete("/companies/{id}/withdrawals/{wid}", brunos, anasWithdrawal).with(asUser(bruno)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Retirada não encontrada."));
    }

    Long company(Long userId, String body) throws Exception {
        return idOf(mockMvc.perform(post("/companies").with(asUser(userId)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn());
    }

    Long me(Long userId, Long companyId) throws Exception {
        String body = mockMvc.perform(get("/companies/{id}/partners", companyId).with(asUser(userId)))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.parse(body).read("$[0].id", Long.class);
    }

    static String body(Long partnerId, String competence, String paymentDate, String gross) {
        return """
                {"partnerId": %d, "kind": "PRO_LABORE", "competenceMonth": "%s", "paymentDate": "%s", "grossAmount": %s}
                """.formatted(partnerId, competence, paymentDate, gross);
    }

    ResultActions proLabore(Long userId, Long companyId, Long partnerId, String competence, String paymentDate, String gross)
            throws Exception {
        return mockMvc.perform(post("/companies/{id}/withdrawals", companyId).with(asUser(userId))
                .contentType(MediaType.APPLICATION_JSON).content(body(partnerId, competence, paymentDate, gross)));
    }
}
