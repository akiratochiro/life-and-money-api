package com.akiratochiro.life_and_money_api.companyoverview;

import com.akiratochiro.life_and_money_api.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O monitor do Fator R (decisão 30). A empresa abriu em 09/2026 no Simples com Fator R; hoje é
 * 12/11/2026, então o monitor olha dezembro, com a janela proporcional de setembro a novembro. Cada mês
 * tem uma nota de 10.000: a receita da janela é 30.000 e o RBT12 de dezembro, 120.000 (faixa 1: III a
 * 6%, V a 15,5%).
 */
class FatorRMonitorIntegrationTest extends IntegrationTest {

    static final String KEY = "35503082212345678000195000000000004226111234567890";

    @BeforeEach
    void fixToday() {
        clock.setTo(Instant.parse("2026-11-12T15:00:00Z"));
    }

    /**
     * Pró-labore de 2.000 em setembro e outubro (registrados) e em novembro (planejado): 6.000 ÷ 30.000
     * = 20%, Anexo V. Falta 28% × 30.000 − 6.000 = 2.400: o planejado vai de 2.000 para 4.400. O DAS de
     * dezembro (sobre 10.000) cai de 1.550 para 600; o INSS sobe 264 (11% de 2.400) e o IRRF é zero nos
     * dois (isento até 5.000 em 2026). Economia = 950 − 264 = 686.
     */
    @Test
    void belowTheThresholdSuggestsRaisingThePlannedProLabore() throws Exception {
        Long ana = register("fatorr.abaixo@test.dev");
        Long company = companyWithHistory(ana, "2000");

        monitor(ana, company)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applies").value(true))
                .andExpect(jsonPath("$.competence").value("2026-12"))
                .andExpect(jsonPath("$.projectedMonth").value("2026-11"))
                .andExpect(jsonPath("$.projection.invoices").value(10000.00))
                .andExpect(jsonPath("$.projection.revenue").value(10000.00))
                .andExpect(jsonPath("$.projection.proLabore").value(2000.00))
                .andExpect(jsonPath("$.projection.otherPayroll").value(0))
                .andExpect(jsonPath("$.window.from").value("2026-09"))
                .andExpect(jsonPath("$.window.to").value("2026-11"))
                .andExpect(jsonPath("$.window.revenue").value(30000.00))
                .andExpect(jsonPath("$.window.payroll").value(6000.00))
                .andExpect(jsonPath("$.ratio").value(0.2))
                .andExpect(jsonPath("$.target").value(0.28))
                .andExpect(jsonPath("$.threshold").value(0.28))
                .andExpect(jsonPath("$.status").value("BELOW_THRESHOLD"))
                .andExpect(jsonPath("$.suggestion.missingPayroll").value(2400.00))
                .andExpect(jsonPath("$.suggestion.partnerId").isNumber())
                .andExpect(jsonPath("$.suggestion.currentProLabore").value(2000.00))
                .andExpect(jsonPath("$.suggestion.suggestedProLabore").value(4400.00))
                .andExpect(jsonPath("$.suggestion.registered").value(false))
                .andExpect(jsonPath("$.suggestion.dasAnnexIII").value(600.00))
                .andExpect(jsonPath("$.suggestion.dasAnnexV").value(1550.00))
                .andExpect(jsonPath("$.suggestion.extraProLaboreTaxes").value(264.00))
                .andExpect(jsonPath("$.suggestion.savings").value(686.00))
                .andExpect(jsonPath("$.notice").isNotEmpty());
    }

    /** Com o pró-labore de novembro já registrado, a sugestão é editar a retirada. */
    @Test
    void aRegisteredProLaboreIsEditedInsteadOfThePlan() throws Exception {
        Long ana = register("fatorr.registrado@test.dev");
        Long company = companyWithHistory(ana, "2000");
        proLabore(ana, company, "2026-11", "2000");

        monitor(ana, company)
                .andExpect(jsonPath("$.suggestion.currentProLabore").value(2000.00))
                .andExpect(jsonPath("$.suggestion.registered").value(true));
    }

    /**
     * Faturamento esperado de 15.000 acima das notas (10.000): a receita de novembro vira 15.000, a da
     * janela 35.000. Falta 9.800 − 6.000 = 3.800. Com 5.000, abaixo das notas, valem as notas.
     */
    @Test
    void theProjectionUsesTheLargerOfInvoicesAndExpectedRevenue() throws Exception {
        Long ana = register("fatorr.esperado@test.dev");
        Long company = companyWithHistory(ana, "2000");

        expectedRevenue(ana, company, "15000");
        monitor(ana, company)
                .andExpect(jsonPath("$.projection.expectedRevenue").value(15000.00))
                .andExpect(jsonPath("$.projection.revenue").value(15000.00))
                .andExpect(jsonPath("$.window.revenue").value(35000.00))
                .andExpect(jsonPath("$.suggestion.missingPayroll").value(3800.00))
                .andExpect(jsonPath("$.suggestion.suggestedProLabore").value(5800.00));

        expectedRevenue(ana, company, "5000");
        monitor(ana, company)
                .andExpect(jsonPath("$.projection.expectedRevenue").value(5000.00))
                .andExpect(jsonPath("$.projection.revenue").value(10000.00));
    }

    /**
     * Salários de novembro: 1.000 pagos e uma fixa de 500 que vence em 20/11, ainda pendente. A folha da
     * janela vai a 7.500 (25%), e falta 8.400 − 7.500 = 900.
     */
    @Test
    void payrollExpensesOfTheMonthCountPaidAndPending() throws Exception {
        Long ana = register("fatorr.folha@test.dev");
        Long company = companyWithHistory(ana, "2000");
        Long salaries = categoryId(ana, company, "Salários e encargos");
        postJson(ana, "/companies/{id}/expenses", company, """
                {"name": "Salário da Bia", "amount": 1000, "categoryId": %d, "date": "2026-11-05"}
                """.formatted(salaries)).andExpect(status().isCreated());
        postJson(ana, "/companies/{id}/recurring-expenses", company, """
                {"name": "FGTS", "amount": 500, "categoryId": %d, "dueDay": 20, "startMonth": "2026-11"}
                """.formatted(salaries)).andExpect(status().isCreated());

        monitor(ana, company)
                .andExpect(jsonPath("$.projection.otherPayroll").value(1500.00))
                .andExpect(jsonPath("$.window.payroll").value(7500.00))
                .andExpect(jsonPath("$.suggestion.missingPayroll").value(900.00))
                .andExpect(jsonPath("$.suggestion.suggestedProLabore").value(2900.00));
    }

    /** 2.900 por mês: 8.700 ÷ 30.000 = 29%. Com a meta de 28%, ok; com 30%, falta 300 e não há economia. */
    @Test
    void betweenTheThresholdAndTheTargetIsOnlyAWarning() throws Exception {
        Long ana = register("fatorr.meta@test.dev");
        Long company = companyWithHistory(ana, "2900");

        monitor(ana, company)
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.suggestion").doesNotExist());

        mockMvc.perform(patch("/companies/{id}", company).with(asUser(ana)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fatorRTarget\": 0.30}"))
                .andExpect(status().isOk());
        monitor(ana, company)
                .andExpect(jsonPath("$.status").value("BELOW_TARGET"))
                .andExpect(jsonPath("$.target").value(0.30))
                .andExpect(jsonPath("$.suggestion.missingPayroll").value(300.00))
                .andExpect(jsonPath("$.suggestion.suggestedProLabore").value(3200.00))
                .andExpect(jsonPath("$.suggestion.savings").doesNotExist());
    }

    @Test
    void itAppliesOnlyToTheSimplesWithFatorR() throws Exception {
        Long ana = register("fatorr.regimes@test.dev");
        for (String regime : List.of(
                "\"legalNature\": \"SLU\", \"taxRegime\": \"SIMPLES_NACIONAL\", \"annexConfig\": \"ANEXO_III_FIXO\"",
                "\"legalNature\": \"MEI\", \"taxRegime\": \"SIMEI\", \"meiActivity\": \"SERVICOS\"",
                "\"legalNature\": \"LTDA\", \"taxRegime\": \"LUCRO_PRESUMIDO\"")) {
            Long company = idOf(postJson(ana, "/companies", null, """
                    {"name": "Outra", "emoji": "🎨", "openingDate": "2026-09-01", %s}
                    """.formatted(regime)).andExpect(status().isCreated()).andReturn());
            monitor(ana, company)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.applies").value(false))
                    .andExpect(jsonPath("$.ratio").doesNotExist());
        }
    }

    /** O regime que conta é o da próxima competência: o Anexo III fixo agendado para dezembro desliga o monitor. */
    @Test
    void aScheduledChangeForTheNextCompetenceCounts() throws Exception {
        Long ana = register("fatorr.agendado@test.dev");
        Long company = companyWithHistory(ana, "2000");
        postJson(ana, "/companies/{id}/regimes", company, """
                {"validFrom": "2026-12", "legalNature": "SLU", "taxRegime": "SIMPLES_NACIONAL", "annexConfig": "ANEXO_III_FIXO"}
                """).andExpect(status().is2xxSuccessful());

        monitor(ana, company).andExpect(jsonPath("$.applies").value(false));
    }

    /** Em dezembro, a competência em jogo é janeiro de 2027, que o app ainda não calcula. */
    @Test
    void theNextYearIsNotCalculatedYet() throws Exception {
        Long ana = register("fatorr.2027@test.dev");
        Long company = companyWithHistory(ana, "2000");
        clock.setTo(Instant.parse("2026-12-10T15:00:00Z"));

        monitor(ana, company)
                .andExpect(jsonPath("$.applies").value(true))
                .andExpect(jsonPath("$.competence").value("2027-01"))
                .andExpect(jsonPath("$.unavailable").value(org.hamcrest.Matchers.containsString("2027")))
                .andExpect(jsonPath("$.ratio").doesNotExist());
    }

    @Test
    void anotherUsersMonitorIsNotFound() throws Exception {
        Long ana = register("fatorr.isolamento.ana@test.dev");
        Long bruno = register("fatorr.isolamento.bruno@test.dev");
        Long anas = companyWithHistory(ana, "2000");

        monitor(bruno, anas).andExpect(status().isNotFound());
    }

    // Apoio

    /** A empresa, uma nota de 10.000 por mês (09 a 11), o pró-labore registrado em 09 e 10 e o planejado. */
    Long companyWithHistory(Long userId, String proLabore) throws Exception {
        Long company = idOf(postJson(userId, "/companies", null, """
                {"name": "Ana Design", "emoji": "🎨", "openingDate": "2026-09-01",
                 "legalNature": "SLU", "taxRegime": "SIMPLES_NACIONAL", "annexConfig": "FATOR_R"}
                """).andExpect(status().isCreated()).andReturn());
        int n = 0;
        for (String month : List.of("2026-09-15", "2026-10-15", "2026-11-05")) {
            n++;
            String key = KEY.substring(0, KEY.length() - 2) + String.format("%02d", 50 + n);
            String xml = xml().replace("NFS" + KEY, "NFS" + key).replace("<nNFSe>42</nNFSe>", "<nNFSe>" + (60 + n) + "</nNFSe>")
                    .replace("<dCompet>2026-10-31</dCompet>", "<dCompet>" + month + "</dCompet>");
            mockMvc.perform(multipart("/companies/{id}/invoices", company)
                            .file(new MockMultipartFile("file", "nota.xml", "application/xml", xml.getBytes(StandardCharsets.UTF_8)))
                            .with(asUser(userId)))
                    .andExpect(status().isCreated());
        }
        proLabore(userId, company, "2026-09", proLabore);
        proLabore(userId, company, "2026-10", proLabore);
        mockMvc.perform(put("/companies/{id}/partners/{pid}/pro-labore-plan", company, partner(userId, company)).with(asUser(userId))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"grossAmount\": " + proLabore + "}"))
                .andExpect(status().isOk());
        return company;
    }

    void proLabore(Long userId, Long companyId, String month, String amount) throws Exception {
        postJson(userId, "/companies/{id}/withdrawals", companyId, """
                {"partnerId": %d, "kind": "PRO_LABORE", "competenceMonth": "%s", "paymentDate": "%s-10", "grossAmount": %s}
                """.formatted(partner(userId, companyId), month, month, amount)).andExpect(status().isCreated());
    }

    void expectedRevenue(Long userId, Long companyId, String amount) throws Exception {
        mockMvc.perform(put("/companies/{id}/expected-revenue", companyId).with(asUser(userId))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\": " + amount + "}"))
                .andExpect(status().isOk());
    }

    Long partner(Long userId, Long companyId) throws Exception {
        return JsonPath.parse(mockMvc.perform(get("/companies/{id}/partners", companyId).with(asUser(userId)))
                .andReturn().getResponse().getContentAsString()).read("$[0].id", Long.class);
    }

    String xml() throws Exception {
        try (var in = getClass().getResourceAsStream("/nfse/nfse-v101.xml")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    ResultActions postJson(Long userId, String path, Long companyId, String body) throws Exception {
        var request = companyId == null ? post(path) : post(path, companyId);
        return mockMvc.perform(request.with(asUser(userId)).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    Long categoryId(Long userId, Long companyId, String name) throws Exception {
        String body = mockMvc.perform(get("/companies/{id}/expense-categories", companyId).with(asUser(userId)))
                .andReturn().getResponse().getContentAsString();
        List<Integer> ids = JsonPath.parse(body).read("$[?(@.name == '" + name + "')].id");
        return ids.getFirst().longValue();
    }

    ResultActions monitor(Long userId, Long companyId) throws Exception {
        return mockMvc.perform(get("/companies/{id}/fator-r", companyId).with(asUser(userId)));
    }
}
