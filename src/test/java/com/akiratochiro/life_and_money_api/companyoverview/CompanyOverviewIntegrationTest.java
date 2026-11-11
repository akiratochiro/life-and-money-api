package com.akiratochiro.life_and_money_api.companyoverview;

import com.akiratochiro.life_and_money_api.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A empresa da Fase 4 (aberta em 01/10/2026, Anexo III fixo), hoje 11/11/2026:
 * <ul>
 *   <li>outubro: nota de 10.000 (ISS retido), avulsa de 200, a fixa "Nuvem" de 120 paga, pró-labore de
 *       3.000 pago em 30/10, e o DAS de 10/2026 pago em 10/11 por 401,50 (despesa de Impostos de novembro);</li>
 *   <li>novembro: a Nuvem pendente e uma taxa personalizada sem valor fixo, que vence dia 10.</li>
 * </ul>
 */
class CompanyOverviewIntegrationTest extends IntegrationTest {

    static final String COMPANY = """
            {"name": "Ana Design", "emoji": "🎨", "openingDate": "2026-10-01",
             "legalNature": "SLU", "taxRegime": "SIMPLES_NACIONAL", "annexConfig": "ANEXO_III_FIXO"}
            """;

    @BeforeEach
    void fixToday() {
        clock.setTo(Instant.parse("2026-11-11T15:00:00Z"));
    }

    /**
     * Outubro: despesas 200 + 120 = 320; impostos da competência = DAS pago 401,50 + DARF previsto 330
     * = 731,50; resultado 10.000 − 320 − 731,50 = 8.948,50. A despesa do DAS (de novembro) não entra.
     */
    @Test
    void aClosedMonthAddsRevenueExpensesAndTheTaxesOfItsCompetence() throws Exception {
        Long ana = register("visao.outubro@test.dev");
        Long company = exampleCompany(ana);

        overview(ana, company, "2026-10")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.open").value(false))
                .andExpect(jsonPath("$.revenue").value(10000.00))
                .andExpect(jsonPath("$.expenses.paid").value(320.00))
                .andExpect(jsonPath("$.expenses.pending").value(0))
                .andExpect(jsonPath("$.taxes.paid").value(401.50))
                .andExpect(jsonPath("$.taxes.expected").value(330.00))
                .andExpect(jsonPath("$.taxes.total").value(731.50))
                .andExpect(jsonPath("$.taxes.withoutExpected").value(0))
                .andExpect(jsonPath("$.result").value(8948.50))
                .andExpect(jsonPath("$.withdrawals.total").value(3000.00))
                .andExpect(jsonPath("$.withdrawals.count").value(1));
    }

    /**
     * Novembro, ainda aberto: sem receita; a despesa de Impostos de 401,50 fica fora (já contou em
     * outubro, pela competência); a Nuvem pendente entra pelo previsto; o DAS de 11/2026 é zero e a taxa
     * sem valor fixo é avisada. Resultado: −120.
     */
    @Test
    void theOpenMonthIsAForecastAndTheTaxExpenseDoesNotCountTwice() throws Exception {
        Long ana = register("visao.novembro@test.dev");
        Long company = exampleCompany(ana);

        overview(ana, company, "2026-11")
                .andExpect(jsonPath("$.open").value(true))
                .andExpect(jsonPath("$.revenue").value(0))
                .andExpect(jsonPath("$.expenses.paid").value(0))
                .andExpect(jsonPath("$.expenses.pending").value(120.00))
                .andExpect(jsonPath("$.taxes.total").value(0))
                .andExpect(jsonPath("$.taxes.withoutExpected").value(1))
                .andExpect(jsonPath("$.result").value(-120.00))
                .andExpect(jsonPath("$.withdrawals.count").value(0));
    }

    /**
     * Até 11/12: atrasados (Nuvem de 05/11, taxa de 10/11) e pendentes (DARF de 19/11, Nuvem de 05/12,
     * taxa de 10/12). O DAS de 10/2026 está pago, e o de 11/2026 vence em 21/12, depois da janela.
     */
    @Test
    void upcomingListsOverdueFirstThenTheNextThirtyDays() throws Exception {
        Long ana = register("visao.vencimentos@test.dev");
        Long company = exampleCompany(ana);

        mockMvc.perform(get("/companies/{id}/upcoming", company).with(asUser(ana)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].name").value("Nuvem"))
                .andExpect(jsonPath("$[0].status").value("OVERDUE"))
                .andExpect(jsonPath("$[0].source").value("EXPENSE"))
                .andExpect(jsonPath("$[1].name").value("Taxa de licença"))
                .andExpect(jsonPath("$[1].status").value("OVERDUE"))
                .andExpect(jsonPath("$[1].amount").value(nullValue()))
                .andExpect(jsonPath("$[2].name").value("DARF do pró-labore (INSS e IRRF)"))
                .andExpect(jsonPath("$[2].dueDate").value("2026-11-19"))
                .andExpect(jsonPath("$[2].amount").value(330.00))
                .andExpect(jsonPath("$[2].status").value("PENDING"))
                .andExpect(jsonPath("$[3].dueDate").value("2026-12-05"))
                .andExpect(jsonPath("$[3].month").value("2026-12"))
                .andExpect(jsonPath("$[4].dueDate").value("2026-12-10"));
    }

    @Test
    void anotherUsersOverviewIsNotFound() throws Exception {
        Long ana = register("visao.isolamento.ana@test.dev");
        Long bruno = register("visao.isolamento.bruno@test.dev");
        Long anas = company(ana);

        overview(bruno, anas, "2026-11").andExpect(status().isNotFound());
        mockMvc.perform(get("/companies/{id}/upcoming", anas).with(asUser(bruno))).andExpect(status().isNotFound());
    }

    // Apoio

    Long exampleCompany(Long userId) throws Exception {
        Long company = company(userId);
        byte[] xml;
        try (var in = getClass().getResourceAsStream("/nfse/nfse-v101.xml")) {
            xml = in.readAllBytes();
        }
        mockMvc.perform(multipart("/companies/{id}/invoices", company)
                        .file(new MockMultipartFile("file", "nota.xml", "application/xml", xml)).with(asUser(userId)))
                .andExpect(status().isCreated());

        Long software = categoryId(userId, company, "Software");
        postJson(userId, "/companies/{id}/expenses", company, """
                {"name": "Cadeira", "amount": 200, "categoryId": %d, "date": "2026-10-15"}
                """.formatted(software)).andExpect(status().isCreated());
        Long nuvem = idOf(postJson(userId, "/companies/{id}/recurring-expenses", company, """
                {"name": "Nuvem", "amount": 120, "categoryId": %d, "dueDay": 5, "startMonth": "2026-10"}
                """.formatted(software)).andExpect(status().isCreated()).andReturn());
        mockMvc.perform(post("/companies/{id}/recurring-expenses/{rid}/occurrences/{m}/payment", company, nuvem, "2026-10")
                        .with(asUser(userId)))
                .andExpect(status().isCreated());

        Long partner = JsonPath.parse(mockMvc.perform(get("/companies/{id}/partners", company).with(asUser(userId)))
                .andReturn().getResponse().getContentAsString()).read("$[0].id", Long.class);
        postJson(userId, "/companies/{id}/withdrawals", company, """
                {"partnerId": %d, "kind": "PRO_LABORE", "competenceMonth": "2026-10", "paymentDate": "2026-10-30", "grossAmount": 3000}
                """.formatted(partner)).andExpect(status().isCreated());

        Long das = obligation(userId, company, "DAS");
        mockMvc.perform(post("/companies/{id}/taxes/{oid}/occurrences/{c}/payment", company, das, "2026-10").with(asUser(userId))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\": 401.50, \"date\": \"2026-11-10\"}"))
                .andExpect(status().isCreated());
        postJson(userId, "/companies/{id}/obligations", company, """
                {"name": "Taxa de licença", "recurrence": "MONTHLY", "dueDay": 10, "dueRule": "NONE", "startMonth": "2026-11"}
                """).andExpect(status().isCreated());
        return company;
    }

    Long company(Long userId) throws Exception {
        return idOf(postJson(userId, "/companies", null, COMPANY).andExpect(status().isCreated()).andReturn());
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

    Long obligation(Long userId, Long companyId, String kind) throws Exception {
        String body = mockMvc.perform(get("/companies/{id}/obligations", companyId).with(asUser(userId)))
                .andReturn().getResponse().getContentAsString();
        List<Integer> ids = JsonPath.parse(body).read("$[?(@.kind == '" + kind + "')].id");
        return ids.getFirst().longValue();
    }

    ResultActions overview(Long userId, Long companyId, String month) throws Exception {
        return mockMvc.perform(get("/companies/{id}/overview", companyId).param("month", month).with(asUser(userId)));
    }
}
