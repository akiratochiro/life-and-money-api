package com.akiratochiro.life_and_money_api.calendar;

import com.akiratochiro.life_and_money_api.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O calendário unificado (decisão 31), hoje 12/11/2026 (sexta, 20/11, é feriado):
 * <ul>
 *   <li>PJ, a empresa "Ana" (aberta em 01/2026, Anexo III fixo): a nota de outubro faz o DAS de 10/2026,
 *       que venceria em 20/11 e passa para 23/11; o pró-labore de outubro faz o DARF, que antecipa para
 *       19/11; a fixa "Contador" vence dia 10 e está atrasada;</li>
 *   <li>PF: "Internet" (dia 5, paga hoje), "Aluguel" (dia 15) e "Cliente fixo" (a receber, dia 20, com prazo
 *       até 23/11).</li>
 * </ul>
 */
class CalendarIntegrationTest extends IntegrationTest {

    static final String DARF = "DARF do pró-labore (INSS e IRRF)";
    static final String DAS = "DAS do Simples Nacional";

    @BeforeEach
    void fixToday() {
        clock.setTo(Instant.parse("2026-11-12T15:00:00Z"));
    }

    @Test
    void theMonthJoinsPersonalBillsAndCompanyTaxesAndExpenses() throws Exception {
        Long ana = register("calendario.mes@test.dev");
        Long company = example(ana);

        november(ana, "ALL", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].title", contains("Internet", "Contador", "Aluguel", DARF, "Cliente fixo", DAS)))
                // A conta paga, com o valor e o dia da transação.
                .andExpect(jsonPath("$[0].scope").value("PF"))
                .andExpect(jsonPath("$[0].status").value("PAID"))
                .andExpect(jsonPath("$[0].paidAmount").value(99.90))
                .andExpect(jsonPath("$[0].paidOn").value("2026-11-12")) // pagar sem data usa hoje
                .andExpect(jsonPath("$[0].link.kind").value("BILLS"))
                .andExpect(jsonPath("$[0].link.month").value("2026-11"))
                // A fixa da empresa, atrasada.
                .andExpect(jsonPath("$[1].source").value("COMPANY_EXPENSE"))
                .andExpect(jsonPath("$[1].company.id").value(company))
                .andExpect(jsonPath("$[1].company.emoji").value("🎨"))
                .andExpect(jsonPath("$[1].detail").value("Despesa fixa"))
                .andExpect(jsonPath("$[1].status").value("OVERDUE"))
                .andExpect(jsonPath("$[1].expectedAmount").value(500.00))
                .andExpect(jsonPath("$[1].link.kind").value("COMPANY_EXPENSES"))
                // O DARF antecipado para a quinta, com o previsto (11% de 3.000).
                .andExpect(jsonPath("$[3].dueDate").value("2026-11-19"))
                .andExpect(jsonPath("$[3].expectedAmount").value(330.00))
                .andExpect(jsonPath("$[3].detail").value("Competência 10/2026"))
                // A conta a receber, com o prazo adiado para o dia útil.
                .andExpect(jsonPath("$[4].direction").value("IN"))
                .andExpect(jsonPath("$[4].detail").value("A receber"))
                .andExpect(jsonPath("$[4].dueDate").value("2026-11-20"))
                .andExpect(jsonPath("$[4].payableUntil").value("2026-11-23"))
                // O DAS que passa do feriado para a segunda.
                .andExpect(jsonPath("$[5].source").value("TAX"))
                .andExpect(jsonPath("$[5].dueDate").value("2026-11-23"))
                .andExpect(jsonPath("$[5].status").value("PENDING"))
                .andExpect(jsonPath("$[5].expectedAmount").isNumber())
                .andExpect(jsonPath("$[5].nothingToPay").value(false))
                .andExpect(jsonPath("$[5].link.kind").value("COMPANY_TAXES"))
                .andExpect(jsonPath("$[5].link.companyId").value(company))
                .andExpect(jsonPath("$[5].link.month").value("2026-11"));
    }

    @Test
    void theFilterSeparatesPersonalFromCompanies() throws Exception {
        Long ana = register("calendario.filtro@test.dev");
        Long company = example(ana);

        november(ana, "PF", null).andExpect(jsonPath("$[*].title", contains("Internet", "Aluguel", "Cliente fixo")));
        november(ana, "PJ", null).andExpect(jsonPath("$[*].title", contains("Contador", DARF, DAS)));
        november(ana, "ALL", company).andExpect(jsonPath("$[*].scope", everyItem(is("PJ")))).andExpect(jsonPath("$", hasSize(3)));
    }

    /** Dezembro: o DAS de 11/2026 (sem nota) sai R$ 0,00 e aparece como nada a pagar. */
    @Test
    void aZeroDasShowsAsNothingToPay() throws Exception {
        Long ana = register("calendario.daszero@test.dev");
        example(ana);

        events(ana, "2026-12-01", "2026-12-31", "PJ", null)
                .andExpect(jsonPath("$[?(@.title == '" + DAS + "')].nothingToPay", contains(true)))
                .andExpect(jsonPath("$[?(@.title == '" + DAS + "')].expectedAmount", contains(0.0)));
    }

    /**
     * Até 19/11: atrasada, só a fixa "Contador"; nos próximos 7 dias, "Aluguel" (15) e o DARF (19).
     * "Cliente fixo" (20) e o DAS (23) ficam para depois; "Internet" está paga.
     */
    @Test
    void remindersAreTheOverdueAndTheNextSevenDays() throws Exception {
        Long ana = register("calendario.lembretes@test.dev");
        example(ana);

        mockMvc.perform(get("/calendar/reminders").with(asUser(ana)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overdue[*].title", contains("Contador")))
                .andExpect(jsonPath("$.nextDays[*].title", contains("Aluguel", DARF)))
                .andExpect(jsonPath("$.count").value(3));
        mockMvc.perform(get("/calendar/reminders").param("scope", "PF").with(asUser(ana)))
                .andExpect(jsonPath("$.count").value(1));
    }

    /**
     * Os limites para trás: na empresa, 3 meses (a fixa desde 07/2026 conta de agosto a novembro); nas
     * contas pessoais, 12 contando o corrente (o condomínio desde 10/2025 conta de dezembro/2025 a
     * novembro/2026), como no Início.
     */
    @Test
    void eachSourceLooksBackAsFarAsItsLimit() throws Exception {
        Long ana = register("calendario.limites@test.dev");
        Long company = company(ana, "Ana");
        Long software = expenseCategory(ana, company, "Software");
        postJson(ana, "/companies/{id}/recurring-expenses", company, """
                {"name": "Contador", "amount": 500, "categoryId": %d, "dueDay": 10, "startMonth": "2026-07"}
                """.formatted(software)).andExpect(status().isCreated());
        Long housing = createCategory(ana, "Casa e contas", "EXPENSE");
        bill(ana, housing, "Condomínio", "400.00", 10, "2025-10");

        mockMvc.perform(get("/calendar/reminders").with(asUser(ana)))
                .andExpect(jsonPath("$.overdue[?(@.title == 'Contador')]", hasSize(4)))
                .andExpect(jsonPath("$.overdue[?(@.title == 'Condomínio')]", hasSize(12)))
                .andExpect(jsonPath("$.overdue[0].dueDate").value("2025-12-10"));
    }

    @Test
    void anArchivedCompanyIsLeftOut() throws Exception {
        Long ana = register("calendario.arquivada@test.dev");
        Long company = company(ana, "Velha");
        Long software = expenseCategory(ana, company, "Software");
        postJson(ana, "/companies/{id}/recurring-expenses", company, """
                {"name": "Contador", "amount": 500, "categoryId": %d, "dueDay": 10, "startMonth": "2026-11"}
                """.formatted(software)).andExpect(status().isCreated());
        mockMvc.perform(post("/companies/{id}/archive", company).with(asUser(ana))).andExpect(status().isOk());

        november(ana, "ALL", null).andExpect(jsonPath("$", hasSize(0)));
        november(ana, "ALL", company).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
    }

    /** Em março, a DEFIS do ano-calendário 2025 entra como declaração, sem valor. */
    @Test
    void declarationsHaveNoAmount() throws Exception {
        clock.setTo(Instant.parse("2026-03-10T15:00:00Z"));
        Long ana = register("calendario.defis@test.dev");
        idOf(postJson(ana, "/companies", null, """
                {"name": "Ana", "emoji": "🎨", "openingDate": "2025-06-01",
                 "legalNature": "SLU", "taxRegime": "SIMPLES_NACIONAL", "annexConfig": "ANEXO_III_FIXO"}
                """).andExpect(status().isCreated()).andReturn());

        events(ana, "2026-03-01", "2026-03-31", "PJ", null)
                .andExpect(jsonPath("$[?(@.source == 'DECLARATION')].detail", contains("Ano-calendário 2025")))
                .andExpect(jsonPath("$[?(@.source == 'DECLARATION')].expectedAmount", contains(org.hamcrest.Matchers.nullValue())));
    }

    @Test
    void theRangeIsLimitedTo62Days() throws Exception {
        Long ana = register("calendario.intervalo@test.dev");

        events(ana, "2026-11-01", "2027-01-15", "ALL", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("O calendário mostra até 62 dias por vez."));
    }

    @Test
    void anotherUsersCompanyIsNotFoundAndTheirBillsDoNotShow() throws Exception {
        Long ana = register("calendario.isolamento.ana@test.dev");
        Long bruno = register("calendario.isolamento.bruno@test.dev");
        Long anas = example(ana);

        november(bruno, "ALL", anas).andExpect(status().isNotFound());
        november(bruno, "ALL", null).andExpect(jsonPath("$", hasSize(0)));
    }

    // Apoio

    Long example(Long userId) throws Exception {
        Long company = company(userId, "Ana");
        byte[] xml;
        try (var in = getClass().getResourceAsStream("/nfse/nfse-v101.xml")) {
            xml = in.readAllBytes();
        }
        mockMvc.perform(multipart("/companies/{id}/invoices", company)
                        .file(new MockMultipartFile("file", "nota.xml", "application/xml", xml)).with(asUser(userId)))
                .andExpect(status().isCreated());
        Long partner = JsonPath.parse(mockMvc.perform(get("/companies/{id}/partners", company).with(asUser(userId)))
                .andReturn().getResponse().getContentAsString()).read("$[0].id", Long.class);
        postJson(userId, "/companies/{id}/withdrawals", company, """
                {"partnerId": %d, "kind": "PRO_LABORE", "competenceMonth": "2026-10", "paymentDate": "2026-10-30", "grossAmount": 3000}
                """.formatted(partner)).andExpect(status().isCreated());
        Long software = expenseCategory(userId, company, "Software");
        postJson(userId, "/companies/{id}/recurring-expenses", company, """
                {"name": "Contador", "amount": 500, "categoryId": %d, "dueDay": 10, "startMonth": "2026-11"}
                """.formatted(software)).andExpect(status().isCreated());

        Long housing = createCategory(userId, "Casa e contas", "EXPENSE");
        Long freelas = createCategory(userId, "Clientes fixos", "INCOME");
        Long internet = bill(userId, housing, "Internet", "99.90", 5, "2026-11");
        mockMvc.perform(post("/bills/{id}/occurrences/{month}/payment", internet, "2026-11").with(asUser(userId)))
                .andExpect(status().is2xxSuccessful());
        bill(userId, housing, "Aluguel", "1500.00", 15, "2026-11");
        bill(userId, freelas, "Cliente fixo", "4000.00", 20, "2026-11");
        return company;
    }

    Long company(Long userId, String name) throws Exception {
        return idOf(postJson(userId, "/companies", null, """
                {"name": "%s", "emoji": "🎨", "openingDate": "2026-01-01",
                 "legalNature": "SLU", "taxRegime": "SIMPLES_NACIONAL", "annexConfig": "ANEXO_III_FIXO"}
                """.formatted(name)).andExpect(status().isCreated()).andReturn());
    }

    Long bill(Long userId, Long categoryId, String name, String amount, int dueDay, String startMonth) throws Exception {
        return idOf(mockMvc.perform(post("/bills").with(asUser(userId)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId": %d, "name": "%s", "amount": %s, "dueDay": %d,
                                 "recurrence": "MONTHLY", "startMonth": "%s", "endMonth": null}
                                """.formatted(categoryId, name, amount, dueDay, startMonth)))
                .andExpect(status().isCreated()).andReturn());
    }

    Long expenseCategory(Long userId, Long companyId, String name) throws Exception {
        String body = mockMvc.perform(get("/companies/{id}/expense-categories", companyId).with(asUser(userId)))
                .andReturn().getResponse().getContentAsString();
        List<Integer> ids = JsonPath.parse(body).read("$[?(@.name == '" + name + "')].id");
        return ids.getFirst().longValue();
    }

    ResultActions postJson(Long userId, String path, Long companyId, String body) throws Exception {
        var request = companyId == null ? post(path) : post(path, companyId);
        return mockMvc.perform(request.with(asUser(userId)).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    ResultActions november(Long userId, String scope, Long companyId) throws Exception {
        return events(userId, "2026-11-01", "2026-11-30", scope, companyId);
    }

    ResultActions events(Long userId, String from, String to, String scope, Long companyId) throws Exception {
        var request = get("/calendar").param("from", from).param("to", to).param("scope", scope).with(asUser(userId));
        if (companyId != null) {
            request = request.param("companyId", companyId.toString());
        }
        return mockMvc.perform(request);
    }
}
